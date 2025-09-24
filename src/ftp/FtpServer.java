package ftp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FtpServer
{
	private ArrayList<FtpTask> clients;
	private ServerSocket serverSocket;
	private int port;
	private Path FTPDir;
	private final Path defaultFTPDir = Paths.get("/FTP");
	public FtpServer(int port)
	{
		System.out.println("-------------------");
		System.out.println("Bienvenid@ a FtpServer.");
		System.out.print("Elige el directorio donde realizar el FTPServer: ");
		BufferedReader usrInput = new BufferedReader(new InputStreamReader(System.in));
		while (true)
		{
			try
			{
				Path tempPath = Paths.get(usrInput.readLine());
				if(!(Files.exists(tempPath) && Files.isDirectory(tempPath)))
				{
					System.out.println("El directorio no existe, quieres crear un nuevo directorio en " + tempPath.toString() + "? (S/N)");
					String input = usrInput.readLine();
					if(input.startsWith("S") || input.startsWith("s") || input.startsWith("y"))
					{
						Files.createDirectory(tempPath);
						FTPDir = tempPath;
						System.out.println("Directorio " + FTPDir.toString() + " creado y asignado correctamente.");
					}
					else
					{
						System.out.println("Se utilizará el directorio estándar del proyecto, no recomendado!");
						if (!Files.exists(defaultFTPDir))
							Files.createDirectories(defaultFTPDir);
						FTPDir = defaultFTPDir;
						System.out.println("Directorio " + FTPDir.toString() + " asignado correctamente");
					}
					break;
				}
				else
				{
					FTPDir = tempPath;
					System.out.println("Directorio " + FTPDir.toString() + " asignado correctamente");
					break;
				}
			} catch (IOException e)
			{
				System.out.println("Error recibiendo el directorio del usuario.");
			}
			
		}
		clients = new ArrayList<FtpTask>();
		try
		{
			System.out.print("Elige el puerto en el que desea hostear el servidor FTP (por defecto 5050): ");
			String input = usrInput.readLine();
			if (input == null || input.equals(""))
				this.port = port;
			else
			{
				int usrPort = Integer.parseInt(input);
				this.port = usrPort;
			}
		} catch (IOException e)
		{
			System.out.println("Error creando puerto " + port);
		}
		try
		{
			serverSocket = new ServerSocket(port);
		} catch(IOException e)
		{
			System.out.println("Server: No se pudo crear el serversocket en el puerto" + port);
		}
	}
	public void runServer()
	{
		ExecutorService es = Executors.newCachedThreadPool();
		while(true)
		{
			System.out.println("Esperando para atender una petición en socket: " + port + "...");
			try
			{
				Socket connection = serverSocket.accept();
				System.out.println("Conexión establecida!");
				clients.add(new FtpTask(this, connection));
				System.out.println("Ejecutando FTPTask en cliente ID #" + clients.getLast().getID());
				es.execute(clients.getLast());
			} catch (IOException e)
			{
				System.out.println("Error aceptando la conexion en el socket.");
			}
		}
	}
	public Path getRootDir() {	return FTPDir;	}
}
