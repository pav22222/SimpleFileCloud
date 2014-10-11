import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class SfcClient {
	private static String host;
	private static int port;
	private String name;
	private String passwd;
	private String rootdir;
	private int tzoffset;
	private File homedir;
	private long lastsync;
	private long server_lastsync;
	final static String meta = ".meta";
	DataOutputStream dos;
	DataInputStream dis;

	public SfcClient() {
		Calendar cal = Calendar.getInstance();
		tzoffset = cal.get(Calendar.ZONE_OFFSET);
	}

	static void deldir(File dir) throws IOException {
		if (dir.isDirectory()) {
			for (File f : dir.listFiles())
				deldir(f);
			dir.delete();
		} else
			dir.delete();
	}

	void readconf(String args0) throws IOException {
		/*
		 * usually server configuration changes rarely so some parameters was
		 * hardcoded here but of cause better way is using a config file
		 */
		host = "192.168.1.34";
		port = 22222;
		/*
		 * TODO Read config data from stdin but using config file probably will
		 * be better
		 */
		Scanner input = new Scanner(System.in);
		System.out.println("name:");
		name = input.nextLine();
		System.out.println("password for " + name + ":");
		passwd = input.nextLine();
		input.close();
		if (args0 == null) {
			rootdir = "./";
			rootdir += name;
		}
		homedir = new File(rootdir);
		if (!homedir.isDirectory() && !homedir.exists()) {
			System.err.println("User directory does not exist");
			System.exit(1);
		}
		System.out.println("Working directory: " + homedir.getCanonicalPath());
	}

	private String localname(File f) {
		String sf = f.getAbsolutePath();
		int idx = homedir.getAbsolutePath().length() + 1;
		return new String(sf.getBytes(), idx, sf.length() - idx);
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
			} else if (dir.isFile()) {
				dos.writeBytes("f\n");
				dos.writeBytes(localname(dir) + "\n");
				String lastmod = Long.toString(modtime);
				dos.writeBytes(lastmod + "\n");
				long size = dir.length();
				dos.writeBytes(Long.toString(size) + "\n");
				FileInputStream fis = new FileInputStream(dir);
				for (long i = 0; i < size; i++) {
					dos.write(fis.read());
				}
				fis.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			System.out.println(e.getLocalizedMessage());
			System.exit(0);
		}
	}

	void sendname(String df, String name, long lastm) {
		try {
			dos.writeBytes("ENTRY\n");
			dos.writeBytes(df + "\n");
			dos.writeBytes(name + "\n");
			String flastmod = Long.toString(lastm) + "\n";
			dos.writeBytes(flastmod);
			recv(df, name, lastm);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			System.out.println(e.getLocalizedMessage());
			System.exit(0);
		}
	}

	void recv(String df, String name, long lastm) {
		try {
			String proto = dis.readLine();
			if (proto.compareTo("EMPTY") == 0) {
				return;
			}
			if (proto.compareTo("FILE") == 0) {
				String label = dis.readLine();
				String filename = dis.readLine();
				String strlastmod = dis.readLine();
				String strsize = dis.readLine();
				int size = Integer.parseInt(strsize);
				long clm = Long.parseLong(strlastmod);
				if (label.compareTo("d") == 0) {
					File dtd = new File(homedir, filename);
					boolean made = dtd.mkdir();
					if (!made)
						dtd.setLastModified(clm + tzoffset);
					recv(df, name, lastm);
				} else if (label.compareTo("f") == 0) {
					File ftd = new File(homedir, filename);
					FileOutputStream fos = new FileOutputStream(ftd);
					for (int i = 0; i < size; i++) {
						fos.write(dis.read());
					}
					ftd.setLastModified(clm + tzoffset);
					fos.close();
					recv(df, name, lastm);
				}
			}
			if (proto.compareTo("FALSE") == 0) {
				File f = new File(homedir, name);
				if (f.isFile()) {
					File parent = f.getParentFile();
					long parentmod = parent.lastModified() - tzoffset;
					if ((lastm < server_lastsync) && (parentmod > lastm)) {
						// if(parentmod>lastm){
						lastm = parentmod;
						f.setLastModified(lastm + tzoffset);
					}
				}
				if (lastm < server_lastsync) {
					deldir(f);
				} else {
					upload(f, lastm);
				}
			}
			if (proto.compareTo("TRUE") == 0) {
				String strslm = dis.readLine();
				long slm = Long.parseLong(strslm);
				if (slm == 0) {
					recv(df, name, lastm);
				} else {
					if (lastm < slm) {
						recv(df, name, lastm);
					} else if (lastm > slm) {
						File f = new File(homedir, name);
						upload(f, f.lastModified());
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	void syncfiles(File dir, String[] s, boolean isHome) {
		for (String i : s) {
			File f = new File(dir, i);
			long ltmp = f.lastModified() - tzoffset;
			if (f.isDirectory()) {
				String[] rd = f.list();
				List<String> l = Arrays.asList(rd);
				Collections.sort(l);
				rd = (String[]) l.toArray();
				sendname("d", localname(f), ltmp);
				syncfiles(f, rd, false);
			}
			if (f.isFile()) {
				sendname("f", localname(f), ltmp);
			}
		}
		int res = dir.compareTo(homedir);
		isHome = (res == 0);
		if (isHome) {
			try {
				dos.writeBytes("EMPTY\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			recv(null, null, 0);
		}
	}

	void client(String args0) {
		try {
			readconf(args0);
			Socket sock = new Socket(host, port);
			InputStream is = sock.getInputStream();
			OutputStream os = sock.getOutputStream();
			dos = new DataOutputStream(os);
			dis = new DataInputStream(is);
			String s = name + "\n";
			dos.writeBytes(s);
			s = passwd + "\n";
			dos.writeBytes(s);
			String line = dis.readLine();

			if (line.equals("OK")) {
				System.out.println("Logged in as " + name);
				File fmeta = new File(rootdir, meta);
				lastsync = fmeta.lastModified() - tzoffset;
				dos.writeBytes(Long.toString(lastsync) + "\n");
				server_lastsync = Long.parseLong(dis.readLine());
				String[] clientfiles = homedir.list();
				List<String> l = Arrays.asList(clientfiles);
				Collections.sort(l);
				clientfiles = (String[]) l.toArray();
				syncfiles(homedir, clientfiles, true);
				// release
				try {
					fmeta.createNewFile();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				long rel = System.currentTimeMillis();
				fmeta.setLastModified(rel);
				dos.writeBytes("BYE\n");
				dos.writeBytes(Long.toString(rel - tzoffset) + "\n");
			} else {
				System.out.println(line);
			}
			is.close();
			os.close();
			sock.close();
			printstat();
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}

	void printstat() {
		// TODO statistics
		System.out.println("Successfully syncronized");
		System.out.println("BYE");
	}

	public static void main(String[] args) {
		SfcClient sfc = new SfcClient();
		String args0 = null;
		if (args.length > 0)
			args0 = args[0];
		sfc.client(args0);
	}
}
