package pro.jaewon.hammer.socket;

import java.net.DatagramSocket;
import java.net.Socket;

public interface IProtectSocket {
	boolean protect(Socket socket);
	boolean protect(int socket);
	boolean protect(DatagramSocket socket);
}
