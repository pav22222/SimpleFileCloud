import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

public class User {
	private static HashSet<String> logged = new HashSet<String>();
	private Socket client;
	private File homedir;
	private int tzoffset;
	private TreeSet<String> server_list;
	private long client_lastsync;
	final static String meta = ".meta";
	private InputStream is;
	private OutputStream os;
	private DataOutputStream dos;
	private DataInputStream dis;
	private long lastsync;

	User(Socket c) {
		this.client = c;
		Calendar cal = Calendar.getInstance();
		tzoffset = cal.get(Calendar.ZONE_OFFSET);
		try {
			is = client.getInputStream();
			os = client.getOutputStream();
			dos = new DataOutputStream(os);
			dis = new DataInputStream(is);
		} catch (IOException e) {
			Thread.currentThread().interrupt();
		}
	}

	protected void auth() {
		try {
			String name = dis.readLine();
			String passwd = dis.readLine();
			boolean bname = Server.users.exists(name);
			boolean bpasswd = false;
			boolean bok = false;
			if (bname) {
				// check password
				String tmp = Server.users.users.get(name);
				bpasswd = passwd.equals(tmp);
			}
			if (bname && bpasswd) {
				if (logged.contains(name)) {
					dos.writeBytes("Alredy logged\n");
				} else
					bok = true;
			}
			if (bok) {
				dos.writeBytes("OK\n");
				logged.add(name);
				homedir = new File(Server.serverRoot, name);
				String[] userfiles = homedir.list();
				List<String> l = Arrays.asList(userfiles);
				server_list = new TreeSet<String>(l);
				client_lastsync = Long.parseLong(dis.readLine());
				File fmeta = new File(homedir, meta);
				lastsync = fmeta.lastModified() - tzoffset;
				dos.writeBytes(Long.toString(lastsync) + "\n");
				serve();
				logged.remove(name);
			} else {
				dos.writeBytes("Access denied\n");
			}
		} catch (IOException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String localname(File f) {
		String sf = f.getAbsolutePath();
		int idx = homedir.getAbsolutePath().length() + 1;
		return new String(sf.getBytes(), idx, sf.length() - idx);
	}

	private void delete(File dir) {
		if (dir.isDirectory()) {
			for (File f : dir.listFiles())
				delete(f);
			dir.delete();
		} else
			dir.delete();
	}

	private void upload(File dir, long modtime) {
		try {
			dos.writeBytes("FILE\n");
			if (dir.isDirectory()) {
				dos.writeBytes("d\n");
				dos.writeBytes(localname(dir) + "\n");
				String lastmod = Long.toString(dir.lastModified() - tzoffset);
				dos.writeBytes(lastmod + "\n");
				dos.writeBytes("0\n");// size
				for (File r : dir.listFiles()) {
					upload(r, r.lastModified());
				}
			} else if (dir.isFile()) {
				dos.writeBytes("f\n");
				dos.writeBytes(localname(dir) + "\n");
				String lastmod = Long.toString(modtime);
				dos.writeBytes(lastmod + "\n");
				FileInputStream fis = new FileInputStream(dir);
				long size = dir.length();
				dos.writeBytes(Long.toString(size) + "\n");
				for (long i = 0; i < size; i++) {
					dos.write(fis.read());
				}
				fis.close();
			}
		} catch (IOException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void serve() {
		while (true) {
			try {
				String proto = dis.readLine();
				if (proto.equals("BYE")) {
					String str = dis.readLine();
					long ls = Long.parseLong(str);
					File fmeta = new File(homedir, ".meta");
					if (!fmeta.exists()) {
						fmeta.createNewFile();
					}
					fmeta.setLastModified(ls + tzoffset);
					// break;
					return;
				}
				if (proto.equals("EMPTY")) {
					while (true) {
						String stmp = server_list.pollFirst();
						if (stmp == null) {
							dos.writeBytes("EMPTY\n");
							break;
						}
						File post = new File(homedir, stmp);
						long slm = post.lastModified() - tzoffset;
						if (post.isFile()) {
							File parent = post.getParentFile();
							long parentmod = parent.lastModified() - tzoffset;
							if ((slm < client_lastsync) && (parentmod > slm)) {
								// if(parentmod>slm){
								slm = parentmod;
								post.setLastModified(slm + tzoffset);
							}
						}
						if (slm < client_lastsync) {
							delete(post);
						} else {
							upload(post, slm);
						}
					}
				}
				if (proto.equals("ENTRY")) {
					String label = dis.readLine();
					String filename = dis.readLine();
					String strlastmod = dis.readLine();
					long clm = Long.parseLong(strlastmod);
					File f = new File(homedir, filename);
					if (f.exists()) {
						String stmp = server_list.pollFirst();
						File pre;
						int res = 0;
						long slm = f.lastModified() - tzoffset;
						if (stmp != null) {
							pre = new File(homedir, stmp);
							res = stmp.compareToIgnoreCase(filename);
						} else {
							pre = null;
						}
						while (res < 0) {
							pre = new File(homedir, stmp);
							if (pre.isFile()) {
								File parent = pre.getParentFile();
								long parentmod = parent.lastModified()
										- tzoffset;
								if ((slm < client_lastsync)
										&& (parentmod > slm)) {
									// if(parentmod>lastm){
									slm = parentmod;
									pre.setLastModified(slm + tzoffset);
								}
							}
							if (client_lastsync > slm) {
								delete(pre);
							} else {
								dos.writeBytes("TRUE\n");
								dos.writeBytes("0\n");
								upload(pre, slm);
							}
							stmp = server_list.pollFirst();
							res = stmp.compareToIgnoreCase(filename);
						}
						if (res > 0)
							server_list.add(stmp);
						if (/* unlikely */slm == 0)
							slm = 1;
						dos.writeBytes("TRUE\n");
						dos.writeBytes(Long.toString(slm) + "\n");
						if (clm < slm) {// client have older version of this
										// file so upload it
							upload(f, f.lastModified());
							dos.writeBytes("EMPTY\n");
						}
					} else {
						dos.writeBytes("FALSE\n");
					}
					continue;
				}
				if (proto.equals("FILE")) {
					String label = dis.readLine();
					String filename = dis.readLine();
					String strlastmod = dis.readLine();
					String strsize = dis.readLine();
					long size = Long.parseLong(strsize);
					long clm = Long.parseLong(strlastmod);
					if (label.compareTo("d") == 0) {
						File dtd = new File(homedir, filename);
						boolean made = dtd.mkdir();
						if (!made)
							dtd.setLastModified(clm + tzoffset);
					} else if (label.compareTo("f") == 0) {
						File ftd = new File(homedir, filename);
						FileOutputStream fos = new FileOutputStream(ftd);
						for (long i = 0; i < size; i++) {
							fos.write(dis.read());
						}
						ftd.setLastModified(clm + tzoffset);
						fos.close();
					}
					continue;
				}
			} catch (IOException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	protected void release() throws IOException {
		is.close();
		os.close();
	}
}
