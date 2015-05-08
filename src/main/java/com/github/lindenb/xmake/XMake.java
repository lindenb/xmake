package com.github.lindenb.xmake;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class XMake
	{
	private static long ID_GENERATOR=1L;
	private final static Logger LOG = Logger.getLogger(XMake.class.getName()); 
	private Document xmlMakefile;
	private File workingDirectory = new File( System.getProperty("user.dir"));
	
	private class MainContext
		extends Context
		{
		MainContext()
			{
			}
		}
	
	
	
	private class DefaultEval
		extends AbstractEval
		{
		private Document dom;
		DefaultEval(Document dom)
			{
			this.dom = dom;
			}
		
		
		@Override
		protected void foundRuleEvent(Rule rule) throws EvalException {
			XMake.this.rules.add(rule);
			}
		
		
		
		public void eval(Context ctx)throws EvalException
			{
			eval(ctx,new NilAppendable());
			}
		
		@Override
		public Appendable eval(Context ctx, Appendable w) throws EvalException {
			Element root=dom.getDocumentElement();
			return _childrenOf(ctx, w,root);
			}
		}
	

	private MainContext mainContext = new MainContext();
	private List<Rule> rules = new  ArrayList<Rule>();
	private Map<String,DepFile> depFilesHash = new  HashMap<String,DepFile>();
	
	private void clear()
		{
		this.rules.clear();
		}
	
	public void compile() throws EvalException
		{
		LOG.info("Compiling");
		DefaultEval parser=new DefaultEval(this.xmlMakefile);
		parser.eval(this.mainContext);
		}
	
	private void usage(PrintStream out)
		{
		System.err.println("Options:");
		}
	
	public void instanceMainWithExit(String args[]) throws EvalException
		{
		System.exit(instanceMain(args));
		}
	
	
	public int instanceMain(String args[]) throws EvalException
		{
		String makefileFile=null;
		LOG.setLevel(Level.INFO);
		
		int optind=0;
		while(optind< args.length)
			{
			if(args[optind].equals("-h") ||
			   args[optind].equals("-help") ||
			   args[optind].equals("--help"))
				{
				usage(System.out);
				return 0;
				}
			else if((args[optind].equals("--file") || args[optind].equals("-f") ) && optind+1 < args.length)
				{
				makefileFile=args[++optind];
				}
			else if((args[optind].equals("-C") ) && optind+1 < args.length)
				{
				this.workingDirectory=new File(args[++optind]);
				}
			else if(args[optind].equals("--log") && optind+1 < args.length)
				{
				LOG.setLevel(Level.parse(args[++optind]));
				}
			else if(args[optind].equals("--"))
				{
				optind++;
				break;
				}
			else if(args[optind].startsWith("-"))
				{
				System.err.println("Unknown option "+args[optind]);
				return -1;
				}
			else 
				{
				break;
				}
			++optind;
			}
		
		
		//search default makefile
		if(makefileFile==null)
			{
			for(String altName:new String[]{"makefile.xml","Makefile.xml","make.xml","xmake.xml"})
				{
				File altFile=new File(altName);
				LOG.info("trying "+altName);
				if(altFile.exists() && altFile.isFile())
					{
					makefileFile=altName;
					break;
					}
				}
			if(makefileFile==null)
				{
				LOG.log(Level.SEVERE,"No input provided");
				return -1;
				}
			}

		
		try {
			DocumentBuilderFactory dbf=DocumentBuilderFactory.newInstance();
			DocumentBuilder db=dbf.newDocumentBuilder();
			if(makefileFile.equals("-"))
				{
				LOG.info("Reading <stdin>");
				this.xmlMakefile =db.parse(System.in);
				}
			else
				{
				LOG.info("Reading "+makefileFile);
				this.xmlMakefile =db.parse(new File(makefileFile));
				}
			} 
		catch (Exception e)
			{
			LOG.log(Level.SEVERE,"Cannot read input "+makefileFile);
			e.printStackTrace();
			return -1;
			}
		
		compile();
		for(Rule rule : this.rules )
			{
			List<String> outputs = rule.getOutputs();
			
			for(String output :   outputs )
				{

				DepFile outDep = this.depFilesHash.get(output);
				if( outDep== null)
					{
					outDep = new DepFile(output);
					this.depFilesHash.put(output,outDep);
					}
				if( outDep.rule != null )
					{
					LOG.warning("Target file defined twice "+outDep);
					System.exit(-1);	
					}
				outDep.rule = rule;
				
			
			
				for(String input :   rule.getInputs())
					{
					DepFile depFile = this.depFilesHash.get(input);
					if( depFile == null)
						{
						LOG.info("Creating target file "+input);
						depFile = new DepFile(input);
						this.depFilesHash.put(input,depFile);
						}
					outDep.prerequisites.add(depFile);
					}
				}
			}


		List<DepFile> L=new ArrayList<>();
		for(DepFile dp:this.depFilesHash.values())
			{
			if(L.isEmpty())
				{
				L.add(dp);
				continue;
				}
			int i = L.size()-1;
			while(i>0 && !dp.hasDependency(L.get(i)))
				{
				--i;
				}
			L.add(i, dp);
			}
		try
			{
			File tmpDir=null;
			for(DepFile dp:L)
				{
				LOG.info("considering "+dp.toString()+" : "+" "+dp.prerequisites+" "+(dp.rule==null));
				Rule rule= dp.rule;
				if(!rule.hasCommand()) continue;
				List<String> requirementsList = rule.getInputs();
				Context ctx=new Context(rule.getContext());
				LOG.info(rule.getContext().toString());
				ctx.put(new SystemVariable(Rule.TARGET_VARNAME, dp.toString()));
				LOG.info("rule.ctx:"+rule.getContext().toString());
				LOG.info("rule.ctx:"+ctx);
				LOG.info("dp:"+dp.toString());
				
				File tmpFile = File.createTempFile("xmake.", ".sh",tmpDir);
				PrintWriter pw=new PrintWriter(tmpFile);
				pw.println("#/bin/bash");
				rule.eval(ctx, pw);
				pw.flush();
				pw.close();
				Runtime.getRuntime().exec("chmod u+x "+tmpFile);
				Process p = new ProcessBuilder().command("/bin/bash",tmpFile.getPath()).start();
				StreamConsummer seInfo = new StreamConsummer(p.getInputStream(), System.out,"[LOGO "+dp+"]");
				StreamConsummer seError = new StreamConsummer(p.getErrorStream(), System.err,"[LOGE "+dp+"]");
	    		seInfo.start();
	    		seError.start();
				int ret= p.waitFor();
				seInfo.join();
	  			seError.join();
				tmpFile.delete();
				LOG.info("done");
				}
			}
		catch(Exception err)
			{
			err.printStackTrace();
			return -1;
			}

		return 0;
		}
	
	public static void main(String args[]) throws Exception
		{
		XMake app = new XMake();
		app.instanceMainWithExit(args); 
		}
	}