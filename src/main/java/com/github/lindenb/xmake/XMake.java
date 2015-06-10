/*
The MIT License (MIT)

Copyright (c) 2014 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


History:
* 2015 creation

*/
package com.github.lindenb.xmake;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * XML based <a href="http://www.gnu.org/software/make/">Make</a>
 * @author Pierre Lindenbaum PhD @lindenb
 *
 */
public class XMake
	{
	/** pattern to split white spaces */
	private static final Pattern WSPACES=Pattern.compile("[\\s]+");
	/** xml namespace */
	public static final String XMLNS="http://github.com/lindenb/xmake/";
	private static final String TARGET_VARNAME="$@";
	private static final String DEPENDENCIES_VARNAME="$^";
	private static final String DEPENDENCY_VARNAME="$<";
	private static long ID_GENERATOR=1L;
	/** Logger */
	private final static Logger LOG = Logger.getLogger(XMake.class.getName()); 
	/** user working directory. Default is System.getProperty("user.dir") */
	private File workingDirectory = new File( System.getProperty("user.dir"));
	/** variable context */
	private Context mainContext = new Context();
	/** list of Rules */
	private List<Rule> rules = new  ArrayList<Rule>();
	/** map target file to DepFile */
	private Map<File,DepFile> depFilesHash = new  HashMap<File,DepFile>();
	/** number of parallele jobs */
	private int numParallelesJobs=1;
	/** dry run */
	private boolean dry_run=true;//TODO
	private boolean only_touch=false;//TODO
	/** option -B, --always-make           Unconditionally make all targets. */
	private boolean always_make=false;
	/**   -k, --keep-going            Keep going when some targets can't be made.*/
	private boolean keep_going=false;
	/** specific target file selected by the user. Null if not defined . When compiling, will be set to the first found target */
	private Set<File> useTargetFiles = null;
	/** tmp directory. Default is System.getProperty("java.io.tmpdir", "/tmp")  */
	private File sysTmpDirectory = new File(System.getProperty("java.io.tmpdir", "/tmp"));
	/** Report HTML file , may be null */
	private File htmlReportFile=null;
	
	/** Thrown during XMake processing */
	@SuppressWarnings("serial")
	private static class EvalException extends Exception
		{
		@SuppressWarnings("unused")
		public EvalException() {
			super();
			}
		@SuppressWarnings("unused")
		public EvalException(String message, Throwable cause,
				boolean enableSuppression, boolean writableStackTrace) {
			super(message, cause, enableSuppression, writableStackTrace);
			}
		@SuppressWarnings("unused")
		public EvalException(String message, Throwable cause) {
			super(message, cause);
			}
	
		public EvalException(String message) {
			super(message);
			}
		
		public EvalException(Node node,String message) {
			super(XMake.toString(node)+" "+message);
			}
		@SuppressWarnings("unused")
		public EvalException(Throwable cause) {
			super(cause);
			}
		}
	
	/** InputStream consummer using when calling a system command
	 * to consumme the stream */
	private static class StreamConsummer
		extends Thread
		{
		/** stream to consumme */
	    private InputStream in;
	    /** where to print */
	    private PrintStream pw;
	    /** prefix to print at the beginning of line, may be null */
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
	    			if(begin && this.prefix!=null) pw.print(prefix);
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
	    		XMake.safeClose(this.in);
	    		}
	    	}
		}

	
	
	private enum Status
		{
		NIL,/** not defined */
		OK, /** target was successfully created */
		QUEUED,/** target is queued */
		RUNNING,/** target is running */
		FATAL,/** target failed */
		IGNORE /** target should be ignored */
		}

	/** type of target , defaut is normal */
	private enum TargetType
		{
		NORMAL,
		PHONY
		};
	
	/** AST Syntax of the XMakefile */
	private class XNode
		{
		/** first child */
		XNode child=null;
		/** next sibling */
		XNode next=null;
		public XNode appendChild(XNode c)
			{
			if(child==null)
				{
				this.child=c;
				}
			else
				{
				XNode curr=this.child;
				while(curr.next!=null) curr=curr.next;
				curr.next=c;
				}
			return this;
			}
		/** eval this node using sb has content, return the result */
		public StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			return evalChildren(sb,ctx);
			}
		
		/** only eval the children, return the result */
		public StringBuilder evalChildren(StringBuilder sb,Context ctx) throws EvalException
			{
			for(XNode c=this.child;c!=null;c=c.next)
				{
				LOG.info("eval "+c.getClass());
				c.eval(sb, ctx);
				}
			return sb;
			}
		/** only eval the children, return the result */
		public String evalChildren(Context ctx) throws EvalException
			{
			return evalChildren(new StringBuilder(),ctx).toString();
			}
		/** only eval the children, split the result and return an array of non-empty strings */
		public List<String> evalChildrenToArray(Context ctx) throws EvalException
			{
			String tokens[]= WSPACES.split(evalChildren(ctx));
			List<String> array=new ArrayList<>(tokens.length);
			for(String s:tokens)
				{
				if(s.isEmpty()) continue;
				array.add(s);
				}
			return array;
			}
		

		/** eval this node using the given context */
		public String eval(Context ctx) throws EvalException
			{
			return eval(new StringBuilder(),ctx).toString();
			}
		
		/** eval this node, split the result and return an array of non-empty strings */
		public List<String> evalToArray(Context ctx) throws EvalException
			{
			String tokens[]= WSPACES.split(eval(ctx));
			List<String> array=new ArrayList<>(tokens.length);
			for(String s:tokens)
				{
				if(s.isEmpty()) continue;
				array.add(s);
				}
			return array;
			}
		}
	
	/** XNode just containing some text */
	private class PlainTextNode extends XNode
		{
		/** text to be stored */
		private String content;
		PlainTextNode(String content)
			{
			this.content = content;
			}
		/** just return the plain text stored */
		@Override
		public StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			return sb.append(this.content);
			}
		}

	/** XNode used to extract a variable */
	private class ValueOf extends XNode
		{
		static final String TAG="value-of";
		private String varName;
		private String defaultValue=null;
		ValueOf(Element root) throws EvalException
			{
			Attr att=root.getAttributeNode("name");
			if(att==null) throw new EvalException(root, "@name missing in <"+TAG+">");
			this.varName=att.getValue().trim();
			if(this.varName.trim().isEmpty())  throw new EvalException(root, "@name cannot be empty in <"+TAG+">");
			att=root.getAttributeNode("default");
			if(att!=null) this.defaultValue=att.getValue();
			}
		
		@Override
		public StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			XNode v = ctx.get(varName);
			if(v!=null)
				{
				v.eval(sb, ctx);
				}
			else if(defaultValue!=null)
				{
				sb.append(this.defaultValue);
				}
			else
				{
				throw new EvalException("variable "+this.varName+" undefined in context and no default defined.");
				}
			return sb;
			}
		}
	/** XNode implementing the target name . Makefile's $@ */
	private class TargetName extends XNode
		{
		private Node root;
		TargetName(Node root)
			{
			this.root=root;
			}
		@Override
		public StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			LOG.info("target name ");
			XNode v = ctx.get(XMake.TARGET_VARNAME);
			if(v!=null)
				{
				LOG.info("target name is "+v.eval(ctx));
				v.eval(sb, ctx);
				}
			else
				{
				throw new EvalException(this.root,"target undefined in context and no default defined.");
				}
			return sb;
			}
		}


	private class Prerequisite  extends XNode
		{
		Node root;
		Prerequisite(Node root)
			{
			this.root=root;
			}
		@Override
		public StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			XNode v = ctx.get(XMake.DEPENDENCY_VARNAME);
			if(v!=null)
				{
				v.eval(sb, ctx);
				}
			else
				{
				throw new EvalException(this.root,"prerequisite undefined in context and no default defined.");
				}
			return sb;
			}
		}
	/** node implementing $^ */
	private class Prerequisites  extends XNode
		{
		Node root;
		Prerequisites(Node root)
			{
			this.root=root;
			}
		@Override
		public StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			XNode v = ctx.get(XMake.DEPENDENCIES_VARNAME);
			if(v!=null)
				{
				v.eval(sb, ctx);
				}
			else
				{
				throw new EvalException(this.root,"prerequisites undefined in context and no default defined.");
				}
			return sb;
			}
		}
	
	/** node implementing $(notdir) */
	private class NotDir  extends XNode
		{
		static final String TAG="notdir";
		@Override
		public StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			boolean first=true;
			for(String token: evalChildrenToArray(ctx))
				{
				if(token.isEmpty()) continue;
				try {
					File f=new File(token);
					if(!first) sb.append(" ");
					first=false;
					sb.append(f.getName());
					} 
				catch (Exception e) {
					LOG.warning(e.getMessage());
					}
				}
			return sb;
			}
		}

	/** implementation */
	private class OnlyDir  extends XNode
		{
		static final String TAG="dir";

		@Override
		public StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			boolean first=true;
			for(String token: evalChildrenToArray(ctx))
				{
				if(token.isEmpty()) continue;
				try {
					File f=new File(token);
					if(f.getParentFile()==null) continue;
					if(!first) sb.append(" ");
					first=false;
					sb.append(f.getParentFile());
					} 
				catch (Exception e) {
					LOG.warning(e.getMessage());
					}
				}
			return sb;
			}
		}

	/** XNode implementing $(trim ) */
	private class TrimNode  extends XNode
		{
		static final String TAG="trim";
		@Override
		public StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			return sb.append(this.evalChildren(ctx).trim());
			}
		}
	private class NormalizeSpaceNode  extends XNode
		{
		static final String TAG="normalize-space";

		@Override
		public StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			return sb.append(this.evalChildren(ctx).replaceAll(WSPACES.pattern()," ").trim());
			}
		}

	/** associative array  Map&lt;String, XNode&gt;
	 * if a key is not, it is searched recursively in the parent's context  */
	private class Context
		{
		/** parent context */
		private Context parent=null;
		private Map<String, XNode> variables = new HashMap<String, XNode>();
		
		/** constructor with parent context */
		Context(Context parent)
			{
			this.parent = parent;
			}
		
		/** constructor without parent context (root context of the application)*/
		Context()
			{
			this(null);
			}
		
		/** get the X node associated to the name
		 * if the key is not found, we search in the parent's context
		 * if the key is not found, we get the System.getProperty(key)
		 * if the key is not found, we get the System.getenv(key)
		 * if the key is not found , we return null
		 * @param name key name
		 * @return the XNode , or null
		 */
		public XNode get(String name)
			{
			XNode v= this.variables.get(name);
			if(v==null)
				{
				if(parent!=null)
					{
					return parent.get(name);
					}
				else
					{
					String s= System.getProperty(name);
					if(s==null ) s = System.getenv(name);
					if(s!=null)
						{
						v=new PlainTextNode(s);
						put(name,v);
						}
					}
				if(v==null)
					{
					LOG.warning("Variable "+name+" not found in context");
					}
				}
			return v;
			}
		
		/** put the XNode associated to the key 'name' */
		public void put(String name,XNode variable)
			{
			this.variables.put(
				name,
				variable
				);
			}
		
		/** shortcut to put(name,PlainTextNode(variable)) */
		public void put(String name,String variable)
			{
			/** wrapped in a void XNode so we can append things */
			XNode n=new XNode();
			n.appendChild(new PlainTextNode(variable));
			this.put( name, n );
			}
		
		/** recursive get keySet */
		public Set<String> keySet()
			{
			Set<String> hash=new HashSet<>();
			Context c=this;
			while(c!=null)
				{
				hash.addAll(c.variables.keySet());
				c=c.parent;
				}
			return hash;
			}
		/** print the keys */
		@Override
		public String toString()
			{ 
			return keySet().toString();
			}
		}

	/**
	 * Dependency File. Wrap a java.io.File
	 * @author lindenb
	 *
	 */
	private class DepFile
		implements Callable<DepFile>
		{	
		/** associated rule to create the target*/
		Rule rule=null;
		/** the file path to be created */
		File file;
		/** current status ; default is NIL */
		Status status = Status.NIL;
		TargetType targetType = TargetType.NORMAL;
		/** list of prerequisites associated to this file */
		Set<DepFile> prerequisites = new LinkedHashSet<>();
		/** the value returned when invoking  the bash shell */
		Future<Integer> returnedValue=null;
		/** shell to be invoked */
		String shellPath="/bin/bash";

		/** constructor with just a file. The rule is unknown for now */
		DepFile(File file)
			{
			this(null,file);
			}
		
		/** constructor : a file and it's rule */
		DepFile(Rule rule,File file)
			{
			this.rule=rule;
			this.file=file;
			}
		
		/** visit the prerequisites, check if there is a loop */
		void checkForLoops() throws EvalException
			{
			_noLoop(new HashSet<DepFile>());
			}
		
		/** called by checkForLoops,  visit the prerequisites*/
		private void _noLoop(Set<DepFile> visited) throws EvalException
			{
			if(visited.contains(this))
				{
				throw new EvalException(
						"Circular definition of "+
								this+":"+visited
						);
				}
			visited.add(this);
			for(DepFile in:this.prerequisites) in._noLoop(visited);
			}
		
		/** decode the prerequisites java.io.Files .
		 * The result is NOT stored in this.prerequisites*/
		public Set<File> getPrerequisiteFiles() throws EvalException
			{
			Set<File> inputFiles=new LinkedHashSet<>();
			if(this.rule!=null)
				{
				/* create a new context */
				Context ctx=new Context(this.rule.getContext());
				/* ... and  set the $@ variable */
				ctx.put(DEPENDENCY_VARNAME, getFile().toString());
				for(String inputf:this.rule.input.evalToArray(ctx))
					{
					File filein = new File(this.rule.getWorkingDirectory(),inputf);
					inputFiles.add(filein);
					}
				}
				
			return inputFiles;
			}
		
		/** returns true if this has a recursive dependency with f */
		public boolean hasDependency(DepFile f)
			{
			if(this.equals(f)) throw new IllegalStateException();//needed ?
			for(DepFile dp:this.prerequisites)
				{
				if(dp.equals(f) || dp.hasDependency(f)) return true;
				}
			return false;
			}
		
		/** return the tmp directory associated to this DepFile . Default return XMake's tmp dir */
		public File getTmpDir()
			{
			return XMake.this.sysTmpDirectory;
			}

		
		/** implementation of call. return this */
		@Override
		public DepFile call() throws Exception
			{
			LOG.info("Setting Status to RUNNING for "+this.file);
			this.status = Status.RUNNING;
			/** stream consummer to echo stdout */
			StreamConsummer stdout=null;
			/** stream consummer to echo stdout */
			StreamConsummer stderr=null;
			/** tmp bash script */
			File tmpFile=null;
			/** used to 'touch' a target */
			FileOutputStream touchStream=null;
			try
				{
				if( XMake.this.only_touch)
					{
					if(this.targetType == TargetType.PHONY)
						{
						LOG.info("Phony target :"+getFile());
						}
					else if(XMake.this.dry_run)
						{
						System.out.println("touch "+this.getFile());
						}
					else
						{
						/* we just open the file to change the timestamp */
						LOG.warning("touch "+this.getFile());
						touchStream = new FileOutputStream(this.getFile(), true);//true==APPEND
						touchStream.flush();
						touchStream.close();
						touchStream=null;
						}
					LOG.info("Setting Status to OK for "+this.file);
					this.status = Status.OK;
					return this;
					}
				Context ctx=new Context(this.rule.getContext());
				ctx.put(TARGET_VARNAME, this.toString());
				
				if(XMake.this.dry_run)
					{
					System.out.println(this.rule.command.eval(ctx));
					LOG.info("Setting Status to OK for "+this.file);
					this.status =Status.OK;
					}
				else
					{
					tmpFile = File.createTempFile("xmake.", ".sh",getTmpDir());
					LOG.info("shell file for "+this.file+" is "+tmpFile);
					
					/* create shell file */
					PrintWriter pw=new PrintWriter(tmpFile);
					pw.println("#/bin/bash");
					pw.append(this.rule.command.eval(ctx));
					pw.flush();
					pw.close();
					if(pw.checkError())
						throw new IOException("An I/O exceptio occured for "+tmpFile);
	
					/* execute the shell command */
					Process p = new ProcessBuilder().command(this.shellPath,tmpFile.getPath()).start();
					stdout = new StreamConsummer(p.getInputStream(), System.out,"[LOG:OUT]");
					stderr = new StreamConsummer(p.getErrorStream(), System.err,"[LOG:ERR]");
					stdout.start();
					stderr.start();
					int ret= p.waitFor();
					stdout.join();
					stderr.join();
					LOG.info("DOne");
					tmpFile.delete();
					this.status = ret==0?Status.OK:Status.FATAL;
					}
				LOG.info("returning "+this.getFile()+" with status="+status);
				return this;
				}
			catch(Exception err)
				{
				this.status = Status.FATAL;
				LOG.warning("Error "+err.getMessage());
				return this;
				}
			finally
				{
				stdout=null;
				stderr=null;
				XMake.safeClose(touchStream);
				//TODO delete file
				}
			}

			
		public File getFile()
			{
			return this.file;
			}
		
		public boolean exists()
			{
			return getFile().exists();
			}
		
		public Set<DepFile> getRequiredBy()
			{
			Set<DepFile> reqs=new HashSet<>();
			for(DepFile dp:XMake.this.depFilesHash.values())
				{
				if(dp.prerequisites.contains(this))
					{
					reqs.add(dp);
					}
				}
			return reqs;
			}
			
		@Override
		public int hashCode()
			{
			return this.file.hashCode();
			}
			
			
		@Override
		public boolean equals(Object o)
			{
			if(o == this) return true;
			if(o ==null || !(o instanceof DepFile)) return false;
			return this.file.equals( DepFile.class.cast(o).file );
			}
		
		@Override
		public String toString()
			{
			return this.file.toString();
			}
		}

	
	private class Rule
		{
		private String id;
		private Context ctx;
		private XNode input = null;
		private XNode output = null;
		private XNode command = null;
		private Set<DepFile> depFiles=null;
		
		public Rule(Context ctx)
			{
			this.id = "r"+(++ID_GENERATOR);
			this.ctx=ctx;
			}
		File getWorkingDirectory()
			{
			return XMake.this.workingDirectory;
			}	
		
		
		void eval(Node root) throws EvalException
			{
			for(Node n1 = root.getFirstChild();
				n1!=null;
				n1=n1.getNextSibling())
				{
				if(_isA(n1, "output"))
					{
					if(this.output!=null) throw new EvalException(root,"output defined twice");
					this.output = new XNode();
					_eval(this.output,Element.class.cast(n1));
					}
				else if(_isA(n1, "input"))
					{
					if(this.input!=null) throw new EvalException(root,"input defined twice");
					this.input = new XNode();
					_eval(this.input,Element.class.cast(n1));
					}
				else if(_isA(n1, "command"))
					{
					if(this.command!=null) throw new EvalException(root,"command defined twice");
					this.command = new XNode();
					_eval(this.command,Element.class.cast(n1));
					}
				}
			if(this.output==null)
				{
				LOG.warning("Not output for "+getId());
				}
			if(this.input==null)
				{
				this.input = new XNode();
				}
			if(this.command==null)
				{
				this.command = new XNode();
				}
			}
		
		public String getId()
			{
			return this.id;
			}
		
		public Context getContext()
			{
			return ctx;
			}
		
		public List<String> getInputs() throws EvalException
			{
			return this.input.evalToArray(getContext());
			}
		
		/** get a set of all the DepFile associated to that Rule.
		 * The result will be cached in 'this.depFiles' */
		public Set<DepFile> getDepFiles()  throws EvalException
			{
			if(this.depFiles==null)
				{
				this.depFiles = new LinkedHashSet<>();
				for(String filename:getOutputs())
					{
					File fileout = new File(getWorkingDirectory(), filename);
					DepFile dep = new DepFile(this,fileout);
					this.depFiles.add(dep);
					}
				}
			return this.depFiles;
			}
		/**  eval(this.output) */
		public List<String> getOutputs() throws EvalException {
			return this.output.evalToArray(getContext());
			}
		
		public boolean hasCommand() throws EvalException 
			{
			return !this.command.eval(getContext()).trim().isEmpty();
			}
				
		}
		
	private void clear()
		{
		this.rules.clear();
		}
	
	private void compile(Document dom) throws EvalException
		{
		LOG.info("Compiling");
		Element root= dom.getDocumentElement();
		if(root == null)
			{
			throw new EvalException("Document contains no root");
			}
		if(!XMLNS.equals(root.getNamespaceURI()))
			{
			throw new EvalException(root,"Bad namespace expected xmlns="+XMLNS+" but got "+root.getNamespaceURI());
			}
		if(!root.getLocalName().equals("xmake"))
			{
			throw new EvalException(root,"Bad root element. expected xmake, got "+root.getLocalName());
			}
		/* validate nodes */
		for(Node n1 = root.getFirstChild();
			n1 != null;
			n1 = n1.getNextSibling())
			{
			if(!_isA(n1,"define")) continue;
			if(!_isA(n1,"rule")) continue;
			throw new EvalException(n1,"node is not handled ");
			}
		/* variables */
		for(Node n1 = root.getFirstChild();
			n1 != null;
			n1 = n1.getNextSibling())
			{
			if(!_isA(n1,"define")) continue;
			_define(Element.class.cast(n1),this.mainContext);
			}
		/* rules */
		for(Node n1 = root.getFirstChild();
			n1 != null;
			n1 = n1.getNextSibling())
			{
			if(!_isA(n1,"rule")) continue;
			_rule(Element.class.cast(n1),this.mainContext);
			}
		}
	
	private boolean _isA(Node node,String localName)
		{
		if(node.getNodeType()!=Node.ELEMENT_NODE) return false;
		if(!XMLNS.equals(node.getNamespaceURI())) return false;
		return (localName.equals(node.getLocalName()));
		}
	private boolean _isBlank(Node node)
		{
		if(node.getNodeType()==Node.COMMENT_NODE) return true;
		if(node.getNodeType()!=Node.TEXT_NODE) return false;
		String content = Text.class.cast(node).getData();
		return content.trim().isEmpty();
		}

	private XNode _attribute_or_element(Element root,String name,boolean required) throws EvalException
		{
		Attr att= root.getAttributeNode(name);
		Element e1=null;
		for(Node n1 = root.getFirstChild();
				n1!=null;
				n1=n1.getNextSibling())
				{
				if(_isA(n1, name))
					{
					if(e1!=null) throw new EvalException(root,"Element <"+name+"/> defined twice");
					e1=Element.class.cast(e1);
					}
				}
		if(att!=null && e1!=null)
			{
			throw new EvalException(root,"Both @"+name+" and <"+name+"/> defined.");
			}
		else if(att==null && e1==null)
			{
			if(required) throw new EvalException(root,"Both @"+name+" and <"+name+"/> defined.");
			return null;
			}
		else if(att!=null)
			{
			return new PlainTextNode(att.getValue());
			}
		else
			{
			XNode n=new XNode();
			_eval(n, e1);
			return n;
			}
		}

	
	private void _assertBlank(Node node)throws EvalException
		{
		if(_isBlank(node)) return ;
		throw new EvalException(node,"not a blank node");
		}

	
	private void _rule(Element root,Context ctx) throws EvalException
		{
		Rule rule = new Rule(ctx);
		rule.eval(root);
		this.rules.add(rule);
		}
	
	private void _define(Element root,Context ctx) throws EvalException
		{
		Attr att=root.getAttributeNode("name");
		if(att==null) throw new EvalException(root, "@name missing");
		XNode parent = new XNode();
		_eval(parent, root);
		ctx.put(att.getName(), parent);
		}

	private void _eval(XNode parent,Element root) throws EvalException
		{
		for(Node n1 = root.getFirstChild();
				n1 != null;
				n1 = n1.getNextSibling())
				{
				switch(n1.getNodeType())
					{
					case Node.COMMENT_NODE:
						break;
					case Node.ELEMENT_NODE:
						Element e1=Element.class.cast(n1);
						if(_isA(e1,"value-of"))
							{
							parent.appendChild(new ValueOf(e1));
							}
						else if(_isA(e1,"target"))
							{
							parent.appendChild(new TargetName(e1));
							}
						else if(_isA(e1,"prerequisite"))
							{
							parent.appendChild(new Prerequisite(e1));
							}
						else if(_isA(e1,"prerequisites"))
							{
							parent.appendChild(new Prerequisites(e1));
							}
						else if(_isA(e1,NotDir.TAG))
							{
							parent.appendChild(new NotDir());
							}
						else if(_isA(e1,TrimNode.TAG))
							{
							parent.appendChild(new TrimNode());
							}
						else if(_isA(e1,NormalizeSpaceNode.TAG))
							{
							parent.appendChild(new NormalizeSpaceNode());
							}
						else if(_isA(e1,OnlyDir.TAG) || _isA(e1,"directory"))
							{
							parent.appendChild(new OnlyDir());
							}
						else
							{
							throw new EvalException(e1,"Unsupported element");
							}
						break;
					case Node.TEXT_NODE:
						parent.appendChild(new PlainTextNode(Text.class.cast(n1).getData()));
						break;
					default:
						throw new EvalException(n1,"Unsupported node");
					}
				}
		}
	
	private DepFile popNextInQueue(List<DepFile> list)
		{
		int i=0;
		while(i< list.size())
			{
			DepFile x = list.get(i);
			if(x.status!=Status.NIL) throw new IllegalStateException("status "+x.status+" "+x.getFile());
			boolean can_pop=true;
			for(DepFile y:list)
				{
				if(x==y) continue;
				if(y.status!=Status.NIL) throw new IllegalStateException();
				if(x.hasDependency(y))
					{
					can_pop=false;
					break;
					}
				}
			if(can_pop)
				{
				list.remove(i);
				return x;
				}
			++i;
			}
		return null;
		}
	
	private static void safeClose(Object...a)
		{
		if(a==null) return;
		for(Object o: a)
			{
			if((o instanceof Closeable))
				{
				try { Closeable.class.cast(o).close(); } catch(Exception e){}
				}
			else
				{
				try {
					Method m = o.getClass().getMethod("close"); 
					m.invoke(o);
					}  catch(Exception e){}
				}
			}
		}
		
	
	/** Convert a dom.Node to String */
	private static String toString(Node n)
		{
		if(n==null) return "";
		switch(n.getNodeType())
			{
			case Node.ATTRIBUTE_NODE: return toString(n.getParentNode())+"/@"+n.getNodeName();
			case Node.TEXT_NODE : return toString(n.getParentNode())+"/#text";
			case Node.ELEMENT_NODE:
				{
				int c=0;
				for(Node n1=n;n1!=null;n1=n1.getPreviousSibling())
					{
					if(n1.getNodeName().equals(n.getNodeName() ))
						++c;
					}
				 return toString(n.getParentNode())+"/"+n.getNodeName()+"["+c+"]";
				}
			default:
				{
				return "";
				}
			}
		}
	/** save the current state of the pipeline as a XHTML file */
	private void dumpXHtmlReport()
		{
		if(this.htmlReportFile==null) return;
		PrintWriter pw=null;
		XMLStreamWriter w=null;
		try {
			pw =  new PrintWriter(this.htmlReportFile, "UTF-8");
			XMLOutputFactory xof= XMLOutputFactory.newFactory();
			w = xof.createXMLStreamWriter(pw);
			w.writeStartElement("html");
			w.writeStartElement("body");
			
			w.writeStartElement("table");
			w.writeStartElement("thead");
			
			w.writeEndElement();//thead

			w.writeStartElement("tbody");
			
			for(DepFile dp:this.depFilesHash.values())
				{
				
				}
			
			w.writeEndElement();//tbdy
			w.writeEndElement();//table

			w.writeEndElement();//body
			w.writeEndElement();//html
			w.flush();
			}
		catch (Exception e) {
			LOG.warning(e.getMessage());
			}
		finally
			{
			safeClose(w,pw);
			}
		}
	
	private void usage(PrintStream out)
		{
		out.println("Options:");
		out.println(" -f FILE, --file FILE  Read FILE as a xmakefile.");
		out.println(" -t, --touch  Touch targets instead of remaking them.");
		out.println(" -n, --just-print, --dry-run, --recon Don't actually run any commands; just print them.");
		out.println(" --report FILE  Save a XHTML report to this file.");
		out.println(" -B, --always-make     Unconditionally make all targets.");
		out.println();
		}
	
	public void instanceMainWithExit(String args[]) throws EvalException
		{
		System.exit(instanceMain(args));
		}
	
	/** main body, returns 0 on success . Called by instanceMainWithExit */
	private int instanceMain(String args[]) throws EvalException
		{
		String makefileFile=null;
		LOG.setLevel(Level.SEVERE);
		
		/* parse arguments */
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
			else if((args[optind].equals("--touch") || args[optind].equals("-t") ))
				{
				this.only_touch = true;
				}
			else if((args[optind].equals("-n") || args[optind].equals("--just-print") || args[optind].equals("--dry-run") || args[optind].equals("--recon")))
				{
				this.dry_run = true;
				}
			else if((args[optind].equals("--always-make") || args[optind].equals("-B") ))
				{
				this.always_make = true;
				}
			else if((args[optind].equals("--keep-going") || args[optind].equals("-k") ))
				{
				this.keep_going = true;
				}
			else if((args[optind].equals("-C") ) && optind+1 < args.length)
				{
				this.workingDirectory=new File(args[++optind]);
				}
			else if((args[optind].equals("--report") ) && optind+1 < args.length)
				{
				this.htmlReportFile=new File(args[++optind]);
				}
			else if((args[optind].equals("--log") || args[optind].equals("--debug")) && optind+1 < args.length)
				{
				LOG.setLevel(Level.parse(args[++optind]));
				}
			else if(args[optind].equals("-d"))
				{
				LOG.setLevel(Level.ALL);
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
		
		
		
		/* parse remaining arguments : targets and variables */
		while(optind < args.length)
			{
			String arg= args[optind];
			
			int eq=arg.indexOf('=');
			if(eq>0)
				{
				this.mainContext.put(arg.substring(0,eq), arg.substring(eq+1));
				}
			else
				{
				File f=new File(this.workingDirectory,arg);
				LOG.info("Adding "+f+" to working directory");
				if(this.useTargetFiles==null) this.useTargetFiles = new LinkedHashSet<>();
				this.useTargetFiles.add(f);
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

		Document xmlMakefile=null;
		try {
			DocumentBuilderFactory dbf=DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			dbf.setXIncludeAware(true);
			dbf.setExpandEntityReferences(true);
			dbf.setIgnoringComments(true);
			DocumentBuilder db=dbf.newDocumentBuilder();
				
			if(makefileFile.equals("-"))
				{
				LOG.info("Reading <stdin>");
				xmlMakefile = db.parse(System.in);
				}
			else
				{
				LOG.info("Reading "+makefileFile);
				xmlMakefile = db.parse(new File(makefileFile));
				}
			} 
		catch (Exception e)
			{
			LOG.log(Level.SEVERE,"Cannot read input "+makefileFile);
			e.printStackTrace();
			return -1;
			}
		
		compile(xmlMakefile);
		
		//create all outputs : rule
		for(Rule rule : this.rules )
			{
			/* one rule can be used by more than one target */
			for(DepFile outDep : rule.getDepFiles())
				{
				/* no specific target file found so far. We use the first met target as the main target */
				if(this.useTargetFiles==null)
					{
					LOG.info("Main target is "+outDep.getFile());
					this.useTargetFiles = new LinkedHashSet<>();
					this.useTargetFiles.add(outDep.getFile());
					}

				/* check if outDep was already declared in this.depFilesHash */
				DepFile prevDeclaration = this.depFilesHash.get(outDep.getFile()) ;
				if(prevDeclaration!=null )
					{
					/* yes it was declared AND associated to a rule */
					if(prevDeclaration.rule!=null)
						{
						LOG.exiting("XMake","compile","Target file defined twice "+outDep+" "+prevDeclaration);
						System.exit(-1);	
						}
					/* no it was not previously declared, we associate the rule */
					else
						{
						/* backward assign, this 'dep' was defined as 'input' but we didn't know
						 it's rule */
						prevDeclaration.rule = rule;
						/* we use the previous declaration */
						outDep= prevDeclaration;
						}
					}
				/* first time we met outDep, we put it in this.depFilesHash */
				else
					{
					this.depFilesHash.put(outDep.getFile(),outDep);
					}
				//create all prerequisites for outDep
				for(File inputFile :   outDep.getPrerequisiteFiles())
					{
					DepFile depFile = this.depFilesHash.get(inputFile);
					if( depFile == null) 
						{
						LOG.info("Creating target file "+inputFile);
						depFile = new DepFile(inputFile);
						this.depFilesHash.put(inputFile,depFile);
						}
					outDep.prerequisites.add(depFile);
					}
				
				}
			}
		//check for loops
		for(DepFile df:this.depFilesHash.values())
			{
			df.checkForLoops();
			}

		// check user target files
		if(this.useTargetFiles!=null)
			{
			Set<DepFile> selTargets = new HashSet<>();
			//check target file exists
			for(File userTargetFile: this.useTargetFiles)
				{
				DepFile depFile = this.depFilesHash.get(userTargetFile);
				if(depFile==null)
					{
					LOG.warning("unknown target file: "+userTargetFile);
					return -1;
					}
				selTargets.add(depFile);
				}
			//flag non-dependant target to status=IGNORE
			for(DepFile depFile:this.depFilesHash.values())
				{
				boolean keep=false;
				
				for(DepFile sel:selTargets)
					{
					if(sel.equals(depFile) || sel.hasDependency(depFile))
						{
						keep=true;
						break;
						}
					}
				// this target will never be called: set status=IGNORE
				if(!keep)
					{
					LOG.info("ignoring "+depFile);
					depFile.status = Status.IGNORE;
					}
				}
			}
		
		
		//check for target without rules
		for(DepFile df:this.depFilesHash.values())
			{
			if(df.rule!=null) continue;
			if(df.status==Status.IGNORE) continue;
			if(df.exists()) continue;
			LOG.exiting("XMake","instanceMain",
					"No rule to build "+df+" required by "+df.getRequiredBy());
			System.exit(-1);	
			}
		
		//check timestamps
		if(!this.always_make)
			{
			for(DepFile df1:this.depFilesHash.values())
				{
				
				if(df1.targetType==TargetType.PHONY)
					{
					continue;
					}
				//don't exist, so make it
				if(!df1.exists())
					{
					continue;
					}
				boolean flag_as_ok=true;
				long timestamp1 = df1.getFile().lastModified();
				
				for(DepFile df2:this.depFilesHash.values())
					{
					if(!df1.hasDependency(df2)) continue;

					if(df2.targetType==TargetType.PHONY)
						{
						flag_as_ok=false;
						break;
						}
					//don't exist, so make it
					if(!df2.exists())
						{
						flag_as_ok=false;
						break;
						}
					/* df2 is a dependence of df1
					  it's timestamp2 should be earlier/lower than timestamp1 t2<t1 */
					long timestamp2 = df2.getFile().lastModified();
					if( timestamp1 < timestamp2)
						{
						flag_as_ok=false;
						break;
						}
					}
				
				if(flag_as_ok)
					{
					LOG.info("No need to remake "+df1);
					df1.status=Status.OK;
					}
				}
			}
		
		//create an array of DepFile
		List<DepFile> targetsQueue = new Vector<>(this.depFilesHash.size());
		//and fill this array
		for(DepFile dp:this.depFilesHash.values())
			{
			if(dp.status==Status.IGNORE) continue;
			targetsQueue.add(dp);
			}
		
		dumpXHtmlReport();
		
		boolean all_targets_successfully_rebuilt=true;
		try
			{
			ExecutorService executor = Executors.newFixedThreadPool(this.numParallelesJobs);
			/* A service that decouples the production of new asynchronous tasks from the consumption of the results of completed tasks.  */
			CompletionService<DepFile> completionService = new ExecutorCompletionService<>(executor );
			/* count number in queue */
			int count_jobs_in_queue = 0;
			
			/* fill the threaded pool */
			while(!targetsQueue.isEmpty() && 
				count_jobs_in_queue <this.numParallelesJobs)
				{
				DepFile found = popNextInQueue(targetsQueue);				
				if(found==null) break;
				LOG.info("queuing "+found);
				found.status=Status.QUEUED;
				completionService.submit(found);
				++count_jobs_in_queue;
				}
			
			/* infinite loop */
			while(!targetsQueue.isEmpty())
				{
			
				
				DepFile nextInQueue = popNextInQueue(targetsQueue);	
				/* nothing to pop , wait 1 sec */
				if(nextInQueue==null) 
					{
					dumpXHtmlReport();
					Thread.sleep(1000L);
					continue;
					}
				
				/* wait for a place to insert the job */
				if(count_jobs_in_queue >= this.numParallelesJobs )
					{
					DepFile dp = completionService.take().get();
					count_jobs_in_queue--;
					
					switch(dp.status)
						{
						case OK: break;
						case FATAL:
							all_targets_successfully_rebuilt = false;
							int y_index=0;
							while(y_index<targetsQueue.size())
								{
								DepFile y = targetsQueue.get(y_index);
								if(y.hasDependency(dp))
									{
									y.status=Status.FATAL;
									targetsQueue.remove(y_index);
									continue;
									}
								else
									{
									++y_index;
									}	
								}
							break;
						default:throw new IllegalStateException();
						}
					
					}
				
				
				if( !all_targets_successfully_rebuilt )
					{
					if(!this.keep_going) break;
					break;
					}
				
				LOG.info("queuing "+nextInQueue);
				nextInQueue.status=Status.QUEUED;
				completionService.submit(nextInQueue);
				++count_jobs_in_queue;	
				dumpXHtmlReport();
				}
			
			/* finish all */
			while(count_jobs_in_queue > 0  &&
				(all_targets_successfully_rebuilt || this.keep_going))
				{
				DepFile dp = completionService.take().get();
				switch(dp.status)
					{
					case OK: break;
					case FATAL:
						all_targets_successfully_rebuilt = false;
						if(!this.keep_going) break;
						break;
					default:throw new IllegalStateException();
					}
				count_jobs_in_queue--;
				dumpXHtmlReport();
				}
			
			executor.shutdown();
			dumpXHtmlReport();

			return all_targets_successfully_rebuilt?0:-1;
			}
		catch(Exception err)
			{
			err.printStackTrace();
			return -1;
			}
		finally
			{
			
			}
		}
	
	/** main: sets the locale, the LOG, creates new XMake and calls instanceMainWithExit */
	public static void main(String args[]) throws Exception
		{
		Locale.setDefault(Locale.US);
		final SimpleDateFormat datefmt=new SimpleDateFormat("yy-MM-dd HH:mm:ss");
		LOG.setUseParentHandlers(false);
		LOG.addHandler(new Handler()
			{
			@Override
			public void publish(LogRecord record) {
				Date now = new Date(record.getMillis());
				System.err.print("["+record.getLevel()+"]");
				System.err.print(" ");
				System.err.print(datefmt.format(now));
				System.err.print(" \"");
				System.err.print(record.getMessage());
				System.err.println("\"");
				if(record.getThrown()!=null)
					{
					record.getThrown().printStackTrace(System.err);
					}
				}
			
			@Override
			public void flush() {
				System.err.flush();
				}
			
			@Override
			public void close() throws SecurityException {
				
				}
			});		
		
		XMake app = new XMake();
		app.instanceMainWithExit(args); 
		}
	}