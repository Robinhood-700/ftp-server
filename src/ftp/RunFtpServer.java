package ftp;

public class RunFtpServer
{
	public static void main(String[] args)
	{
		int port = 5050;
		FtpServer ftpServer = new FtpServer(port);
		ftpServer.runServer();
	}
}
