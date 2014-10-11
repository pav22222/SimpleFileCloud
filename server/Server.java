import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

public class Server implements Runnable {

	private static File conf;
	private static int serverPort;
	static ServerSocket sock;
	static File serverRoot;
	private static boolean status = false;
	static Scanner s = new Scanner(System.in);
	protected static Users users;
	private static Server singleton = null;
	private static HashSet<Thread> connected = new HashSet<Thread>();

	public static Server getSingleton() {
		if (singleton == null)
			singleton = new Server();
		return singleton;
	}

	private static void readconf() {
		BufferedReader br = null;
		try {
			FileReader fr = new FileReader(conf);
			br = new BufferedReader(fr);
			String line;
			while ((line = br.readLine()) != null) {
				if (line.isEmpty())
					continue;
				if (line.charAt(0) == '#')
					continue;
				if (!line.contains("=")) {
					System.out.println("Wrong config file format in line: "
							+ line);
					break;
				}
				if (line.contains("serverRoot")) {
					serverRoot = new File(line.substring(line.indexOf("=") + 1));
				} else if (line.contains("serverPort")) {
					serverPort = Integer.parseInt(line.substring(line
							.indexOf("=") + 1));
				}
			}
		} catch (FileNotFoundException e) {
			System.out.println("File not found: " + conf.toString());
		} catch (IOException e) {
			System.out.println("Unable to read file: " + conf.toString());
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				System.out.println("Unable to close file: " + conf.toString());
			} catch (NullPointerException ex) {
				// File was probably never opened
			}
		}

	}

	private static void quit() {
		stop();
		System.exit(0);
	}

	private static void adduser(String name) {
		System.out.println("Enter password for user " + name + ":");
		String passwd = s.nextLine();
		System.out.println("Confirm password:");
		String confirm = s.nextLine();
		if (!passwd.equals(confirm)) {
			System.out.println("doesn`t match");
			return;
		}
		if (users.exists(name)) {
			System.out.println("User " + name + " exists.");
			return;
		}
		users.add(name, passwd);

	}

	private static void start() {
		if (status) {
			System.out.println("Alredy running");
			return;
		}
		readconf();
		try {
			sock = new ServerSocket(serverPort);
			Thread t = new Thread(getSingleton());
			t.start();
			connected.add(t);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		status = true;
		System.out.println("started");
	}

	private static void stop() {
		if (!status) {
			System.out.println("Not running");
			return;
		}
		for (Thread t : connected) {
			t.interrupt();
		}
		try {
			if (sock.isBound())
				sock.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		status = false;
		System.out.println("stopped");
	}

	private static void rmuser(String name) {
		System.out.println("Not implemented yet");
	}

	public static void main(String[] args) {
		boolean quitting = false;
		if (args.length > 0) {
			System.out.println("Use 1st parameter as a config filename");
			conf = new File(args[0]);
		} else {
			conf = new File("sfc.conf");
		}
		start();
		users = new Users();
		final String[] shell = new String[] { "adduser", "rmuser", "start",
				"stop", "status", "quit" };
		while (true) {
			String input = s.nextLine();
			String cmd = input;
			String name;
			int p = input.indexOf(" ");
			if (p != -1)
				cmd = input.substring(0, p);

			int cmdidx;
			for (cmdidx = 0; cmdidx < 6; cmdidx++) {
				if (cmd.compareToIgnoreCase(shell[cmdidx]) == 0)
					break;
			}
			switch (cmdidx) {
			case 0:// adduser
				if (p == -1) {
					System.out.println("Command format: adduser name");
					break;
				}
				name = input.substring(p + 1);
				adduser(name);
				break;
			case 1:// rmuser
				name = input.substring(p + 1);
				rmuser(name);
				break;
			case 2:// start
				start();
				break;
			case 3:// stop
				stop();
				break;
			case 4:// status
				if (status)
					System.out.println("running");
				else
					System.out.println("not running");
				break;
			case 5:// quit
				s.close();
				quitting = true;
				System.out.println("gonna quit...");
				quit();
				break;
			default:
				break;
			}
			;
			if (quitting) {
				System.out.println("Bye");
				break;
			}
		}
	}

	@Override
	public void run() {
		Socket client = null;
		try {
			client = sock.accept();
			Thread t = new Thread(getSingleton());
			t.start();
			connected.add(t);
			//
			User user = new User(client);
			user.auth();
			user.release();
			client.close();
			t = Thread.currentThread();
			connected.remove(t);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			Thread.currentThread().interrupt();
		}
	}
}

class Users {
	HashMap<String, String> users;

	Users() {
		users = new HashMap<String, String>();
		File list = new File(Server.serverRoot, "users");
		if (!list.exists()) {
			// create empty users list
			try {
				list.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		// read list file
		try {
			FileReader fr = new FileReader(list);
			BufferedReader br = new BufferedReader(fr);
			String entry;
			while ((entry = br.readLine()) != null) {
				if (entry.isEmpty()) {
					System.out.println("empty entry");
					break;
				}
				int tab = entry.indexOf('\t');
				if (tab == -1) {
					System.err.println("Invalid user list format");
					break;
				}
				users.put(entry.substring(0, tab), entry.substring(tab + 1));
			}

			fr.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	boolean exists(String name) {
		if (users.get(name) != null)
			return true;
		return false;
	}

	void passwd(String name, String passwd, String newpasswd) {
		// Change user password
		System.out.println("Not implemented yet");
	}

	void add(String name, String passwd) {
		File list = new File(Server.serverRoot, "users");
		FileWriter fw = null;
		try {
			fw = new FileWriter(list, true);
		} catch (IOException e) {
			System.err.println("Failure");
			e.printStackTrace();
		}
		try {
			fw.append(name);
			fw.append('\t');
			fw.append(passwd);
			fw.append('\n');
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// create user directory
		File dir = new File(Server.serverRoot, name);
		boolean res = dir.mkdir();
		if (!res) {
			System.out.println("Can`t create user " + name + ". Alredy exist?");
		} else {
			System.out.println("User " + name + " created");
		}
	}
}
