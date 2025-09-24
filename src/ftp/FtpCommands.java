package ftp;

public enum FtpCommands 
{
	list,
	upload,
	download,
	mkdir,
	dirup,
	changedir,
	remove,
	copy,
	move,
	unknown,
	cat,
	ready,			//escondido del usuario, solo para usarlo de control
	exit;
	
	public static FtpCommands whatCommand(String command)
	{
		if (command.equalsIgnoreCase("list") || command.equalsIgnoreCase("dir"))
			return list;
		else if (command.equalsIgnoreCase("upload") || command.equalsIgnoreCase("up"))
			return upload;
		else if (command.equalsIgnoreCase("download") || command.equalsIgnoreCase("down"))
			return download;
		else if (command.equalsIgnoreCase("mkdir") || command.equalsIgnoreCase("mk"))
			return mkdir;
		else if (command.equals("..") || command.equalsIgnoreCase("dirup"))
			return dirup;
		else if (command.contains("change") || command.contains("CHANGE") || command.equalsIgnoreCase("cd"))
			return changedir;
		else if (command.equalsIgnoreCase("remove") || command.equalsIgnoreCase("rm") || command.equalsIgnoreCase("del") || command.equalsIgnoreCase("delete"))
			return remove;
		else if (command.equalsIgnoreCase("move") || command.equalsIgnoreCase("mv"))
			return move;
		else if (command.equalsIgnoreCase("copy") || command.equalsIgnoreCase("cp"))
			return copy;
		else if (command.equalsIgnoreCase("cat"))
			return cat;
		else if (command.equalsIgnoreCase("ready"))
			return ready;
		else if (command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("q") || command.equalsIgnoreCase("ex"))
			return exit;
		else
			return unknown;
	}
	public static String toString(FtpCommands command)
	{
		switch(command)
		{
		case list:
			return "list";
		case upload:
			return "upload";
		case download:
			return "download";
		case mkdir:
			return "mkdir";
		case dirup:
			return "dirup";
		case changedir:
			return "cd";
		case remove:
			return "remove";
		case move:
			return "move";
		case copy:
			return "copy";
		case cat:
			return "cat";
		case ready:
			return "ready";
		case exit:
			return "exit";
		default:
			return "???";
		}
	}
}
