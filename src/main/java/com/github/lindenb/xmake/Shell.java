package com.github.lindenb.xmake;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

public class Shell
	implements Callable<Integer>
	{
	int ret=-1;
	int command;
	String shellPath="/bin/bash";
	private File tmpFile;
	
	public Shell()
		{
		
		}
	
	
	@Override
	public Integer call()
		{
		try
			{
			System.err.println("running "+command);
			Thread.sleep(10*100);
			Process p = new ProcessBuilder().command("ls"+(command%10==0?"xxx":""),"/tmp").start();
			StreamConsummer seInfo = new StreamConsummer(p.getInputStream(), System.out,"[LOGO "+command+"]");
			StreamConsummer seError = new StreamConsummer(p.getErrorStream(), System.err,"[LOGE "+command+"]");
    		seInfo.start();
    		seError.start();
			ret= p.waitFor();
			seInfo.join();
  			seError.join();
			System.err.println("DOne");
			return ret;
			}
		catch(Exception err)
			{
			System.err.println(err.getMessage());
			return -1;
			}
		
		}	}
