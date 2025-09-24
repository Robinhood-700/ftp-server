package ftp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FtpClient
{
	private Socket socket;
	private BufferedReader userIn;
	private Path userPath;
	private PrintWriter serverPW;
	private BufferedReader serverBR;
	private DataOutputStream serverDOS;
	private DataInputStream serverDIS;
	public FtpClient()
	{
		System.out.println("-----------------------");
		System.out.println("Bienvenid@ al cliente FTP!");
		System.out.print("Inserte la dirección IP del servidor o localhost para conectarse al dispositivo local: ");
		userIn = new BufferedReader(new InputStreamReader(System.in));
		String input = "";
		String address;
		//primero recibo la direccion a la que el cliente se quiere conectar, o la maquina local
		try
		{
			input = userIn.readLine();
			if (input.equals("") || input == null || input.equalsIgnoreCase("loc") || input.equalsIgnoreCase("local")
					|| input.equalsIgnoreCase("localhost"))
			{
				address = "127.0.0.1";
			} else
			{
				address = input;
			}
			System.out.print("Inserte el puerto del servidor al que desea conectar (por defecto 5050): ");
			input.equals("");
			int port = 5050;
			//ahora el puerto, por defecto el 5050
			input = userIn.readLine();
			if (input.equals("") || input == null)
			{
				//es un poco redundante, pero me parece mas intuitivo que hacer la condicion del reves
				port = 5050;
			}
			else
			{
				port = Integer.parseInt(input);
			}
			socket = new Socket(address, port);
			System.out.println("Conexión establecida en " + socket);
		} catch (UnknownHostException e)
		{
			System.out.println("Error creando el socket.");
		} catch (IOException e)
		{
			System.out.println("Error recibiendo el input del usuario.");
		}
	}
	private void setDir()
	//recibe el directorio root del USUARIO. Es la carpeta desde donde se suben los archivos al servidor y a donde se descargan
	{
		System.out.print("Elige el directorio root para realizar subida y descarga de archivos en tu dispositivo: ");
		while (true)
		{
			try
			{
				Path tempPath = Paths.get(userIn.readLine());
				if(!(Files.exists(tempPath) && Files.isDirectory(tempPath)))
				{
					System.out.println("El directorio no existe, quieres crear un nuevo directorio en " + tempPath.toString() + "? (S/N)");
					String input = userIn.readLine();
					if(input.startsWith("S") || input.startsWith("s") || input.startsWith("y"))
					{
						Files.createDirectory(tempPath);
						userPath = tempPath;
						System.out.println("Directorio " + userPath.toString() + " creado y asignado correctamente.");
						break;
					}
					else
					{
						System.out.println("Por favor, inserte de nuevo el directorio deseado:");
					}
				}
				else
				{
					userPath = tempPath;
					System.out.println("Directorio " + userPath.toString() + " asignado correctamente");
					break;
				}
			} catch (IOException e)
			{
				System.out.println("Error recibiendo el input del usuario.");
			}
		}
	}
	private void listCommands()
	//Muestra en pantalla todos los comandos posibles y unas instrucciones basicas de uso
	{
		System.out.println();
		System.out.println("Comandos Disponibles:");
        System.out.println("---------------------");
        System.out.println("list       - Muestra el contenido del directorio actual.");
        System.out.println("upload     - Sube un archivo desde el cliente al servidor.");
        System.out.println("download   - Descarga un archivo desde el servidor al cliente.");
        System.out.println("mkdir      - Crea un nuevo directorio en el servidor.");
        System.out.println("dirup      - Sube un nivel en el árbol de directorios.");
        System.out.println("changedir  - Cambia el directorio actual al especificado.");
        System.out.println("remove     - Elimina un archivo o directorio en el servidor.");
        System.out.println("copy       - Copia un archivo o directorio en el servidor.");
        System.out.println("move       - Mueve un archivo o directorio a una nueva ubicación.");
        System.out.println("cat        - Muestra en pantalla el contenido de un archivo .txt.");
        System.out.println("usrdir     - Cambia el directorio de la maquina local.");
        System.out.println("exit       - Cierra la conexión y termina el programa.");
        System.out.println("help       - Muestra esta información.");
        System.out.println("---------------------");
        System.out.println("Los comandos list, dirup, usrdir, exit y help no necesitan mas argumentos. En los demás comandos hay que insertar el archivo"
        		+ "/directorio destino, y seguir las instrucciones mostradas en pantalla.");
        System.out.println("---------------------");
	}
	private String getUserCommand()
	{
		try
		{
			return userIn.readLine();
		} catch (IOException e)
		{
			System.out.println("No se pudo leer el el comando.");
			return "";
		}
	}
	private void sendReady()
	//este metodo envia la senal ready al servidor para que se prepare para recibir un comando
	{
		FtpCommands command = FtpCommands.ready;
		serverPW.println(FtpCommands.toString(command));
		serverPW.flush();
		//intentamos leer el caracter y mostrarlo en pantalla, nos devuelve el directorio actual del servidor
		try
		{
			System.out.print(serverBR.readLine() + " >> ");
		} catch (IOException e)
		{
			System.out.println("Error leyendo respuesta del servidor.");
		}
	}
	private void listCommand() throws IOException
	//muestra los archivos y carpetas del directorio actual del servidor
	{
		serverPW.println(FtpCommands.toString(FtpCommands.list));
		serverPW.flush();
		String response = "";
		while (response != null)
		{
			try
			{
				response = serverBR.readLine();
				if (response != null && !response.equals("END"))
					System.out.println(response);
				else break;
			} catch (IOException e)
			{
				System.out.println("Error recibiendo respuesta del servidor.");
			}
		}
		sendReady();
	}
	private int uploadToServer(Path source) throws IOException
	//resoibsable de subir las carpetas y los archivos al servidor, devuelve el numero de carpetas/archivos subidos
	{
		int numberUploaded = 0;
		//si source es un archivo, enviamos la informacion que necesita el servidor y el archivo
		if (!Files.isDirectory(source))
		{
			serverPW.println("FILE");
			serverPW.println(source.getFileName().toString());
			serverPW.flush();
			System.out.println("Sending file " + source.toString());
			serverPW.flush();
			serverDOS.writeLong(Files.size(source));
			System.out.println("Size " + Files.size(source));
			serverDOS.flush();
			try (FileInputStream fileIn = new FileInputStream(source.toFile()))
			{
				byte[] buffer = new byte[4096];
				int bytesRead = 0;
				while ((bytesRead = fileIn.read(buffer)) != -1)
				{
					serverDOS.write(buffer, 0, bytesRead);
					System.out.println("Bytes read: " + bytesRead + " remaining: " + (Files.size(source) - bytesRead));
				}
				serverDOS.flush();
			}
			if (serverBR.readLine().equals("OK"))
			{
				++numberUploaded;
			}
			else
			{
				System.out.println("Error recibiendo respuesta del servidor.");
			}
		}
		//si es un directorio, enviamos solo el nombre del directorio, y enviamos uno a uno todos los archivos y directorios contenidos en el
		//mediante una llamada recursiva
		else
		{
			serverPW.println("DIR");
			serverPW.flush();
			serverPW.println(source.getFileName().toString());
			System.out.println("Sending directory " + source.toString());
			serverPW.flush();
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(source))
			{
				for (Path archive : stream)				//sube todos los archivos/directorios uno a uno
				{
					numberUploaded += uploadToServer(archive);
				}
				//enviamos END DIR para que el servidor sepa que se ha enviado el ultimo archivo contenido en la carpeta y ha de subir al directorio padre
				serverPW.println("END_DIR");
				serverPW.flush();
			} catch (IOException e)
			{
				System.out.println("Error subiendo archivos de " + userPath.resolve(source).toString() + ".");
			}
			++numberUploaded;
		}
		return numberUploaded;
	}
	private void uploadCommand(String argument) throws IOException
	//verifica el input del usuario y si es una ruta valida llama al metodo responsable
	{
		Path source = userPath.resolve(argument);
		if (Files.exists(source))
		{
			serverPW.println(FtpCommands.toString(FtpCommands.upload));
			serverPW.flush();
			int uploaded = uploadToServer(source);
		 	//tenemos que informarle al servidor cuando hemos terminado de subir archivos
			serverPW.println("END"); 
			serverPW.flush();
			System.out.println(uploaded + " archivos/directorios enviados al servidor correctamente.");
		}
		else
		{
			System.out.println("El archivo o directorio" + userPath.resolve(source).toString() + " no existe. Por favor inténtelo de nuevo.");
		}
		sendReady();
	}
	private void downloadCommand(String argument) throws IOException
	//descarga un archivo/carpeta del servidor
	{
		serverPW.println(FtpCommands.toString(FtpCommands.download));
		serverPW.flush();
		serverPW.println(argument);
		serverPW.flush();
		String response = serverBR.readLine();
		if (response.equals("OK"))
		{
			System.out.println("Iniciando descarga de archivos...");
			int downloaded = 0;
			// el primer archivo o directorio siempre se va a subir al userPath
			Path downloadDir = userPath; 
			try
			{
				while (true)
				{
					String input = serverBR.readLine();
					if (input.equals("END"))
					{
						//si he descargado el ultimo archivo de la carpeta, salgo del bucle
						break;
					} else if (input.equals("FILE"))
					{	//si es un archivo lo descargamos del servidor
						String fileName = serverBR.readLine();
						long fileSize = serverDIS.readLong();
						Path destination = downloadDir.resolve(fileName);
						System.out.println("Descargando archivo " + destination.toString() + ", tamaño: " + fileSize + " bytes.");
						//System.out.println("FILE " + userPath.relativize(destination).toString() + " recibido, archivo #" + (downloaded + 1));
						try (FileOutputStream fileOut = new FileOutputStream(destination.toFile()))
						{
							byte[] buffer = new byte[4096];
							long remaining = fileSize;
							int bytesRead = 0;
							while (remaining > 0 && (bytesRead = serverDIS.read(buffer, 0,
									(int) Math.min(buffer.length, remaining))) > 0)
							{
								fileOut.write(buffer, 0, bytesRead);
								remaining -= bytesRead;
								System.out.println("Bytes read: " + bytesRead + " Remaining: " + remaining);
							}
							downloaded++;
							serverPW.println("OK");
							serverPW.flush();
						}
					} else if (input.equals("DIR"))
					{	//si es una carpeta lo creamos en el cliente
						//System.out.println("DIR recibido, archivo #" + (downloaded + 1));
						String dirName = serverBR.readLine();
						Path destination = downloadDir.resolve(dirName);
						Files.createDirectories(destination);
						downloadDir = destination; // cambiamos el directorio actual al nuevo para recibir los archivos
													// de este directorio
						//System.out.println("Changed downloadDir to " + downloadDir.toString());
						++downloaded;
					} else if (input.equals("END_DIR"))
					{
						//System.out.println("END_DIR recibido, " + downloaded + " archivos y directorios descargados de momento.");
						downloadDir = downloadDir.getParent(); // cuando se sube el ultimo archivo de un directorio,
																// volvemos a la carpeta padre para seguir recibiendo
																// archivos
					} else
					{
						System.out.println("CODIGO ERRONEO RECIBIDO: " + input);
						break;
					}
				}
			} catch (IOException e)
			{
				System.out.println("IOException en comando download.");
			}
			System.out.println(downloaded + " archivos/directorios descargados del servidor correctamente.");
		}
		else
		{
			System.out.println(response);
		}
		sendReady();
	}
	private void mkdirCommand(String argument) throws IOException
	//crea una carpeta nueva en el servidor
	{
		serverPW.println(FtpCommands.toString(FtpCommands.mkdir));
		serverPW.flush();
		if (argument == null || argument.equals(""))
		{
			System.out.print("Inserte el nombre de la carpeta que desea crear: ");
			argument = userIn.readLine();
		}
		serverPW.println(argument);
		serverPW.flush();
		try
		{
			System.out.println(serverBR.readLine());
		} catch (IOException e)
		{
			System.out.println("Error recibiendo respuesta del servidor.");
		}
		sendReady();
	}
	private void dirupCommand()
	//sube una carpeta mas arriba en el servidor, equivalente a cd ..
	{
		serverPW.println(FtpCommands.toString(FtpCommands.dirup));
		serverPW.flush();
		try
		{
			System.out.println(serverBR.readLine());
		} catch (IOException e)
		{
			System.out.println("Error recibiendo respuesta del servidor.");
		}
		sendReady();
	}
	private void changedirCommand(String argument) throws IOException
	//cambia el directorio mostrado del servidor
	{
		serverPW.println(FtpCommands.toString(FtpCommands.changedir));
		serverPW.flush();
		if (argument == null || argument.equals(""))
		{
			System.out.print("Inserte el nombre de la carpeta a la que desea navegar: ");
			argument = userIn.readLine();
		}
		serverPW.println(argument);
		serverPW.flush();
		try
		{
			System.out.println(serverBR.readLine());
		} catch (IOException e)
		{
			System.out.println("Error recibiendo respuesta del servidor.");
		}
		sendReady();
	}
	private void removeCommand(String argument) throws IOException
	//elimina un directorio/carpeta
	{
		serverPW.println(FtpCommands.toString(FtpCommands.remove));
		serverPW.flush();
		if (argument == null || argument.equals(""))
		{
			System.out.print("Inserte el nombre del archivo o directorio que desea eliminar: ");
			argument = userIn.readLine();
		}
		serverPW.println(argument);
		serverPW.flush();
		try
		{
			System.out.println(serverBR.readLine());
		} catch (IOException e)
		{
			System.out.println("Error recibiendo respuesta del servidor.");
		}
		sendReady();
	}
	private void copyCommand(String argument) throws IOException
	{
		serverPW.println(FtpCommands.toString(FtpCommands.copy));
		serverPW.flush();
		if (argument == null || argument.equals(""))
		{
			System.out.print("Inserte el nombre del archivo o directorio que desea copiar: ");
			argument = userIn.readLine();
		}
		serverPW.println(argument);
		serverPW.flush();
		System.out.print("Por favor inserta la ruta destino: ");
		String destination = userIn.readLine();
		serverPW.println(destination);
		serverPW.flush();
		try
		{
			System.out.println(serverBR.readLine());
		} catch (IOException e)
		{
			System.out.println("IOException en copy.");
		}
		sendReady();
	}
	private void moveCommand(String argument) throws IOException
	{
		serverPW.println(FtpCommands.toString(FtpCommands.move));
		serverPW.flush();
		if (argument == null || argument.equals(""))
		{
			System.out.print("Inserte el nombre del archivo o directorio que desea mover: ");
			argument = userIn.readLine();
		}
		serverPW.println(argument);
		serverPW.flush();
		System.out.print("Por favor inserta la ruta destino: ");
		String destination = userIn.readLine();
		serverPW.println(destination);
		serverPW.flush();
		try
		{
			System.out.println(serverBR.readLine());
		} catch (IOException e)
		{
			System.out.println("IOException en move.");
		}
		sendReady();
	}
	private void catCommand(String argument) throws IOException
	{
		serverPW.println(FtpCommands.toString(FtpCommands.cat));
		serverPW.flush();
		if (argument == null || argument.equals(""))
		{
			System.out.print("Inserte el nombre del archivo o directorio que desea mover: ");
			argument = userIn.readLine();
		}
		serverPW.println(argument);
		serverPW.flush();
		String response = "";
		while (!(response = serverBR.readLine()).equals("END"))
		{
			System.out.println(response);
		}
		
		sendReady();
	}
	private void exitCommand()
	{
		System.out.println("Cerrando la conexion con el servidor...");
		serverPW.println(FtpCommands.toString(FtpCommands.exit));
		serverPW.flush();
	}
	public void runClient()			//Ahora que el socket esta establecido y el BR para recibir los comandos del usuario del teclado, podemos ejecutar comandos
	{
		System.out.println("FtpClient: Ejecutando cliente...");
		//Preguntamos por el directorio root en client side y lo asignamos
		setDir();
		try
		{				//Cuendo se establece la conexion con el servidor, espero al mensaje de bienvenida
			serverBR = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			System.out.println(serverBR.readLine());
		} catch (UnknownHostException e)
		{
			System.out.println("Error recibiendo el inputStream del socket.");
		} catch (IOException e)
		{
			System.out.println("Error recibiendo la respuesta del servidor.");
		}
		//Mostramos la lista de posibles comandos al usuario
		listCommands();
		//Creamos un PrintWriter para enviarle los comandos al servidor
		try
		{
			serverPW = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
			//tambien creamos el DataOutputStream y DataInputStream para cuando subimos y bajamos archivos posteriormente
			serverDOS = new DataOutputStream(socket.getOutputStream());
			serverDIS = new DataInputStream(socket.getInputStream());
			//enviamos el comando ready para comprobar el funcionamiento, si el servidor devuelve el caracter / sabemos que esta funcionando correctamente
			FtpCommands command = FtpCommands.ready;
			sendReady();
			do
			{
				String input = getUserCommand();
				String[] temp = input.split("\\s+", 2); //este comando separa el string en comando y argumento, s+ es separar por espacios, y 2 es max 2 partes
				String commandString = temp[0];
				String argumentString = (temp.length > 1) ? temp[1] : "";
				command = FtpCommands.whatCommand(commandString);
				switch (command)
				{
				case list:
					listCommand();
					break;
				case upload:
					uploadCommand(argumentString);
					break;
				case download:
					downloadCommand(argumentString);
					break;
				case mkdir:
					mkdirCommand(argumentString);
					break;
				case dirup:
					dirupCommand();
					break;
				case changedir:
					changedirCommand(argumentString);
					break;
				case remove:
					removeCommand(argumentString);
					break;
				case copy:
					copyCommand(argumentString);
					break;
				case move:
					moveCommand(argumentString);
					break;
				case cat:
					catCommand(argumentString);
					break;
				case exit:
					exitCommand();
					break;
				default:
					if (commandString.equalsIgnoreCase("help") || commandString.equalsIgnoreCase("h"))
						listCommands();
					else if (commandString.equalsIgnoreCase("usrdir") || commandString.equalsIgnoreCase("usr"))
						setDir();
					else
						System.out.println("Comando desconocido, por favor pruebe otra vez");
					sendReady();
					break;
				}
			} while (command != FtpCommands.exit);
		} catch(IOException e)
		{
			System.out.println("Error creando PrintWriter del servidor.");
		}
		try
		{
			serverBR.close();
			serverPW.close();
			socket.close();
			System.out.println("Conexion finalizada correctamente. Cerrando el cliente...");
			userIn.close();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
