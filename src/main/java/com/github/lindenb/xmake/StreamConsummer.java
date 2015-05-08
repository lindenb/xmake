package com.github.lindenb.xmake;

import java.io.InputStream;
import java.io.PrintStream;

public class StreamConsummer
	extends Thread
	{
    private InputStream in;
    private PrintStream pw;
	private String prefix;
	
    public StreamConsummer(
    		InputStream in,
    		PrintStream pw,
    		String prefix
    		)
		{
        this.in = in;
        this.pw = pw;
        this.prefix=prefix;
    	}
	
    @Override
    public void run()
    	{
    	boolean begin=true;
    	try {
    		int c;
    		while((c=in.read())!=-1)
    			{
    			if(begin) pw.print(prefix);
    			pw.write((char)c);
    			begin=(c=='\n');
    			}
    	 	}
    	catch(Exception err)
    		{
    		err.printStackTrace();
    		}
    	finally
    		{
    		try{in.close();} catch(Exception e2) {}
    		}
    	}
	}
