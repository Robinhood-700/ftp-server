package ftp;

import java.io.BufferedReader;
import java.nio.file.*;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.net.Socket;

public class FtpTask implements Runnable
{
	private Socket socket;
	private FtpServer ftpServer;		//este import no se usa, pero estaba pensado para poder implementar monitoreo. Si un cliente hace un cambio
	private Path rootDir;				//se lo podria informar a los demas clientes mediante un metodo de FtpServer
	private Path currentDir;
	private Path relativePath;
	private static int idCounter = 1;
	private final int id;
	private PrintWriter userPW;
	private BufferedReader userBR;
	private DataInputStream userDIS;
	private DataOutputStream userDOS;
	public FtpTask(FtpServer server, Socket s)
	{
		id = idCounter++;
		socket = s;
		ftpServer = server;
		rootDir = server.getRootDir();
		currentDir = rootDir;
		System.out.println("FtpTask id " + id + " creado.");
	}
	public final int getID()
	{
		return id;
	}
	private FtpCommands receiveCommand()
	//recibe uno de los comandos permitidos del usuario y lo devuelve a run()
	{
		try
		{
			System.out.println("FTPTask id #" + id + " : Esperando para recibir comando.");
			String input = userBR.readLine();
			//no hay que validar el input ya que se hace en el cliente
			return FtpCommands.whatCommand(input);
		} catch(IOException e)
		{
			System.out.println("FtpTask: Error recibiendo comando del cliente.");
			e.printStackTrace();
			return FtpCommands.unknown;
		}
	}
	private void sendCurrentDir()
	{
		//este metodo envia el directorio actual y caracteres adicionales para mejorar la visualizacion del cliente
		relativePath = rootDir.relativize(currentDir).normalize();
		userPW.println("\\" + relativePath.toString());
		userPW.flush();
	}
	private void doList()
	{
		//el relativize hace que el usuario no pueda ver mas arriba que el directorio FTP
		userPW.println("dir --- \\" + rootDir.relativize(currentDir).toString());
		//muestra en pantalla cada archivo/directorio en el directorio actual
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir))
		{
			for (Path archive : stream)
			{
				userPW.print(rootDir.relativize(archive).toString() + "\t\t-> ");
				if (Files.isDirectory(archive))
					userPW.println("d");
				else
					userPW.println("a");
			}
			userPW.println("------------");
			userPW.println("END");
			userPW.flush();
		} catch (IOException e)
		{
			System.out.println("Error en comando LIST");
		}
		
	}
	private void doUpload()
	{
		try
		{
			int uploaded = 0;
			//el primer archivo o directorio siempre se va a subir al currentDir
			Path uploadDir = currentDir;
			while (true)
			{
				String input = userBR.readLine();
				//Si ha terminado la subida de archivos, salgo del bucle
				if (input.equals("END"))
				{
					System.out.println("END recibido, " + uploaded + " archivos subidos correctamente.");
					break;
				}
				//si el cliente envia un archivo
				else if (input.equals("FILE"))
				{
					String fileName = userBR.readLine();
					long fileSize = userDIS.readLong();
					Path destination = uploadDir.resolve(fileName);
					System.out.println("Receiving file " + destination.toString() + " size: " + fileSize);
					System.out.println("FILE " + rootDir.relativize(destination).toString() + " recibido, archivo #" + (uploaded + 1));
					try (FileOutputStream fileOut = new FileOutputStream(destination.toFile()))
					{
						byte[] buffer = new byte[4096];
						long remaining = fileSize;
						int bytesRead = 0;
						while (remaining > 0 && (bytesRead = userDIS.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0)
						{
							fileOut.write(buffer, 0, bytesRead);
							remaining -= bytesRead;
							System.out.println("Bytes read: " + bytesRead + " Remaining: " + remaining);
						}
						uploaded++;
						userPW.println("OK");
						userPW.flush();
					}
				}
				//si el cliente envia una carpeta
				else if (input.equals("DIR"))
				{
					System.out.println("DIR recibido, archivo #" + (uploaded + 1));
					String dirName = userBR.readLine();
					Path destination = uploadDir.resolve(dirName);
					Files.createDirectories(destination);
					uploadDir = destination;					//cambiamos el directorio actual al nuevo para recibir los archivos de este directorio
					System.out.println("Changed uploadDir to " + uploadDir.toString());
					++uploaded;
				}
				//el cliente marca el final de la carpeta, todos los archivos de esa carpteta se han subido
				else if (input.equals("END_DIR"))
				{
					System.out.println("END_DIR recibido, " + uploaded + " archivos y directorios subidos de momento.");
					//cuando se sube el ultimo archivo de un directorio, volvemos a la carpeta padre para seguir recibiendo archivos
					uploadDir = uploadDir.getParent();
				}
				else
				{
					System.out.println("CODIGO ERRONEO RECIBIDO: " + input);
				}
			}
		} catch (IOException e)
		{
			System.out.println("IOException en comando upload.");
		}
	}
	private int downloadFiles(Path source) throws IOException
	//sube los archivos uno a uno y crea las carpetas necesarias, devuelve el numero de archivos y carpetas que se descargan del servidor
	{
		int numberDownloaded = 0;
		//si no es un directorio, envia el archivo. Solo termina la ejecucion y devuelve 1 cuando el cliente manda el OK de que ha recibido el archivo
		if (!Files.isDirectory(source))
		{
			userPW.println("FILE");
			userPW.println(source.getFileName().toString());
			userPW.flush();
			System.out.println("Sending file " + source.toString());
			System.out.println("Size " + Files.size(source));
			userDOS.writeLong(Files.size(source));
			userDOS.flush();
			try (FileInputStream fileIn = new FileInputStream(source.toFile()))
			{
				byte[] buffer = new byte[4096];
				int bytesRead = 0;
				while ((bytesRead = fileIn.read(buffer)) != -1)
				{
					userDOS.write(buffer, 0, bytesRead);
					System.out.println("Bytes read: " + bytesRead + " remaining: " + (Files.size(source) - bytesRead));
				}
				userDOS.flush();
			}
			if (userBR.readLine().equals("OK"))
			{
				++numberDownloaded;
			}
			else
			{
				System.out.println("Error recibiendo respuesta del ususario.");
			}
		}
		//si es un directorio, crea un stream que llama recursivamente a la funcion para cada archivo en la carpeta
		else
		{
			userPW.println("DIR");
			userPW.println(source.getFileName().toString());
			System.out.println("Sending directory " + source.toString());
			userPW.flush();
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(source))
			{
				//sube todos los archivos/directorios uno a uno
				for (Path archive : stream)	
				{
					numberDownloaded += downloadFiles(archive);
				}
				//esta senal sirve para que el cliente sepa que ha llegado hasta el final de la carpeta y deberia volver a la carpeta padre
				userPW.println("END_DIR");
				userPW.flush();
			} catch (IOException e)
			{
				System.out.println("Error desde archivos de " + rootDir.resolve(source).toString() + ".");
			}
			++numberDownloaded;
		}
		return numberDownloaded;
	}
	private void doDownload()
	//comprueba que existe el archivo o la carpeta que el cliente quiere descargar, y en ese caso llama a la funcion responsable
	{
		try
		{
			String input = userBR.readLine();
			Path downloadDir = currentDir.resolve(input);
			if (!Files.exists(downloadDir))
			{
				userPW.println("El archivo o directorio " + rootDir.relativize(downloadDir).toString()
						+ " no existe. Por favor intentelo de nuevo.");
				userPW.flush();
			} else
			{
				userPW.println("OK");
				userPW.flush();
				System.out.println("Iniciando transferencia de archivos del servidor al cliente...");
				int downloaded = downloadFiles(downloadDir);
				userPW.println("END");
				userPW.flush();
				System.out.println(downloaded + " archivos/directorios enviados al cliente correctamente.");
			}
		} catch (IOException e)
		{
			System.out.println("IOException en comando download.");
		}
	}
	private void doMkdir()
	{
		try
		{
			String input = userBR.readLine();
			Path newDir = currentDir.resolve(input);
			Files.createDirectory(newDir);
			userPW.println("Directory created successfully. Path: " + newDir.toString());
			userPW.flush();
		} catch (IOException e)
		{
			System.out.println("Error making directory");
		}
	}
	private void doDirup()
	//equivalente a cd .., sube al directorio padre
	{
		if (currentDir.equals(rootDir))
		{
			userPW.println("No se puede salir fuera de la ruta raíz.");
		} else
		{
			 // cambia el directorio actual al padre
			currentDir = currentDir.resolve("..").normalize();
			userPW.println("Directorio cambiado correctamente.");
		}
		userPW.flush();
	}
	private void doChangeDir()
	//cambia el directorio actual segun el input del usuario
	{
		try
		{
			String input = userBR.readLine();
			//el normalize() quita los regex del string, por ej ".."
			Path newDir;
			if (input.equals("/"))
				newDir = rootDir;
			else
				newDir = currentDir.resolve(input).normalize(); 
			if (Files.exists(newDir))
			{
				Path absoluteRoot = rootDir.toAbsolutePath().normalize();
				Path absoluteNew = newDir.toAbsolutePath().normalize();
				if (absoluteNew.startsWith(absoluteRoot))
				{
					currentDir = newDir;
					userPW.println("Directorio cambiado con éxito.");
				}
				else
					userPW.println("El directorio insertado es ilegal. Por favor inserta un directorio dentro del root.");
			}
			else
			{
				userPW.println("El directorio insertado no existe.");
			}
		} catch (IOException e)
		{
			System.out.println("IOExceptioin en changedir.");
		}
		userPW.flush();
	}
	private int deleteDirectory(Path dir)	
	//elimina el directorio indicado y devuelve el numero de archivos eliminados
	{
		int sum = 0;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir))
		{
			for (Path p : stream)
			{
				if (Files.isDirectory(p))
				{
					//llamada recursiva si el directorio contiene otro directorio
					sum += deleteDirectory(p); 
				} else
				{
					Files.delete(p);
					++sum;
				}
			}
			Files.delete(dir);
		} catch (IOException e)
		{
			System.out.println("IO Error in deleteDirectory.");
		}
		return sum;
	}
	private void doRemove()
	//elimina el archivo o directorio indicado
	{
		try
		{
			String input = userBR.readLine();
			Path newPath = currentDir.resolve(input);
			if(Files.exists(newPath))
			{
				if (Files.isDirectory(newPath))
				{
					int sum = 0;
					sum += deleteDirectory(newPath);
					userPW.println("Directorio " + rootDir.relativize(newPath).toString() + " y " + sum + " archivos contenidos eliminados correctamente.");
				}
				else
				{
					Files.delete(newPath);
					userPW.println("File: " + rootDir.relativize(newPath).toString() + " deleted successfully.");
				}
			}
			else
			{
				userPW.println("La ruta " + rootDir.relativize(newPath).toString() + " no existe.");
			}
		} catch (IOException e)
		{
			System.out.println("IOException en doRemove()");
		}
		userPW.flush();
	}
	private void doMove()
	//mueve (o renombra) un archivo
	{
		try
		{
			String argument = userBR.readLine();
			String dest = userBR.readLine();
			Path source = currentDir.resolve(argument);
			Path destination;
			if (dest.equals("") || dest.equals("/"))
			{
				destination = rootDir;
			}
			else
			{
				destination = rootDir.resolve(dest);
			}
			if (!Files.exists(source))
			{
				userPW.println("El archivo o directorio " + source.toString() + " no existe, por favor inténtelo de nuevo... ");
				userPW.flush();
				return;
			}
			//si el destino es un directorio y no existe. Se podria crear la carpeta pero complica mas la implementacion
			if (Files.isDirectory(destination) && !Files.exists(destination))
			{
				userPW.println("El destino " + destination.toString() +  " no existe, por favor inténtelo de nuevo o crea la carpeta destino... ");
				userPW.flush();
				return;
			}
			//compruebo que tanto el origen como el destino estan dentro de la carpeta FTP
			Path absoluteSrc = source.toAbsolutePath();
			Path absoluteDst = destination.toAbsolutePath();
			if (!(absoluteDst.startsWith(rootDir)) || !(absoluteSrc.startsWith(rootDir)))
			{
				userPW.println("El destino y/o el archivo no está contenido en el directorio root.");
				userPW.flush();
				return;
			}
			if (destination.startsWith(source)) 
			{
			    userPW.println("No se puede mover un directorio a uno de sus subdirectorios.");
			    userPW.flush();
			    return;
			}

			if (Files.isDirectory(destination))
			{
				destination = destination.resolve(source.getFileName());
			}
			//esta opcion la encontre en internet, es para que sobreescriba si hay un archivo en el destino en vez de crear una excepcion
			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
			userPW.println("El archivo o directorio se ha movido correctamente de " + rootDir.relativize(source).toString() + " a " + rootDir.relativize(destination).toString() + ".");
			userPW.flush();
		} catch (IOException e)
		{
			System.out.println("IOException en doMove.");
		}
	}
	private void copyRecursive(Path source, Path destination) throws IOException
	{
		if (!Files.exists(destination))
		{
			Files.createDirectories(destination);
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(source))
		{
			for (Path archive : stream)
			{
				Path targetPath = destination.resolve(archive.getFileName());
				if (Files.isDirectory(archive))
				{
					copyRecursive(archive, targetPath);
				} 
				else
				{
					Files.copy(archive, targetPath, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}
	private void doCopy()
	{
		try
		{
			String argument = userBR.readLine();
			String dest = userBR.readLine();
			Path source = currentDir.resolve(argument);
			Path destination;
			if (dest.equals("") || dest.equals("/"))
			{
				destination = rootDir;
			}
			else
			{
				destination = rootDir.resolve(dest);
			}
			if (!Files.exists(source))
			{
				userPW.println("El archivo o directorio " + source.toString() +" no existe, por favor inténtelo de nuevo... ");
				userPW.flush();
				return;
			}
			if (!Files.exists(destination))
			{
				userPW.println("El destino " + destination.toString() + " no existe, por favor inténtelo de nuevo o crea la carpeta destino... ");
				userPW.flush();
				return;
			}
			Path absoluteSrc = source.toAbsolutePath();
			Path absoluteDst = destination.toAbsolutePath();
			if (!(absoluteDst.startsWith(rootDir)) || !(absoluteSrc.startsWith(rootDir)))
			{
				userPW.println("El destino y/o el archivo no está contenido en el directorio root.");
				userPW.flush();
				return;
			}
			if (destination.startsWith(source)) 
			{
			    userPW.println("No se puede copiar un directorio a uno de sus subdirectorios.");
			    userPW.flush();
			    return;
			}
			if (Files.isDirectory(destination))
			{
				destination = destination.resolve(source.getFileName());
			}
			if (Files.isDirectory(source))
			{
				copyRecursive(source, destination);
			}
			else
			{
				Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
			}
			//esta opcion la encontre en internet, es para que sobreescriba si hay un archivo en el destino en vez de crear una excepcion
			userPW.println("El archivo o directorio se ha copiado correctamente de " + rootDir.relativize(source).toString() + " a " + rootDir.relativize(destination).toString() + ".");
			userPW.flush();
		} catch (IOException e)
		{
			System.out.println("IOException en doCopy.");
		}
	}
	private void doCat() 
	{
		try
		{
			String argument = userBR.readLine();
			Path source = currentDir.resolve(argument);
			if (!Files.exists(source))
			{
				userPW.println("El archivo " + rootDir.relativize(source).toString() + " no existe.");
			}
			else if (!(source.toString().endsWith(".txt")))
			{
				userPW.println("El archivo " + rootDir.relativize(source).toString() + " no es un archivo de texto. "
						+ " Por favor prueba otra vez con un archivo .txt.");
			}
			else
			{
				userPW.println("---------------");
				BufferedReader cat = new BufferedReader(new InputStreamReader(new FileInputStream(source.toFile())));
				String line = "";
				while ((line = cat.readLine()) != null)
				{
					userPW.println(line);
				}
				userPW.println("---------------");
				cat.close();
			}
			userPW.println("END");
			userPW.flush();
		} catch (IOException e)
		{
			System.out.println("IOException en doCat.");
		}
	}
	private void doExit()
	{
		System.out.println("Cerrando la conexion con cliente #" + id);
	}
	private void doUnknown()
	{
		System.out.println("Se ha recibido un comando desconocido.");
	}
	public void run()
	{
		try
		{
			//Preparo el UserOut y envio el mensaje de bienvenida, luego creo el userIn para la respuesta
			userPW = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
			userPW.write("Bienvenid@ cliente #" + id + " al servidor FTP.\n");
			userPW.flush();
			userBR = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			//preparo el DataInputStream y DataOutputStream para cuand luego se suban y bajen archivos
			userDOS = new DataOutputStream(socket.getOutputStream());
			userDIS = new DataInputStream(socket.getInputStream());
		} catch (IOException e)
		{
			System.out.println("Error recibiendo respuesta del cliente.");
		}
		FtpCommands command;
		//este bucle recorre el switch hasta que el usuario teclee el comando quit, lee el comando del usuario y ejecuta el metodo correspondiente
		do
		{
			command = receiveCommand();
			System.out.println("Comando " + FtpCommands.toString(command) + " recibido");
			switch (command)
			{
			case ready:
				sendCurrentDir();
				break;
			case list:
				doList();
				break;
			case upload:
				doUpload();
				break;
			case download:
				doDownload();
				break;
			case mkdir:
				doMkdir();
				break;
			case dirup:
				doDirup();
				break;
			case changedir:
				doChangeDir();
				break;
			case remove:
				doRemove();
				break;
			case copy:
				doCopy();
				break;
			case move:
				doMove();
				break;
			case cat:
				doCat();
				break;
			case exit:
				doExit();
				break;
			default:
				doUnknown();
				break;
			}
		} while (command != FtpCommands.exit);
		try
		{
			userBR.close();
			userPW.close();
			socket.close();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
