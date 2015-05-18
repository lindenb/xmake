package com.github.lindenb.xmake;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class XMake
	{
	private static final Pattern WSPACES=Pattern.compile("[\\s]+");
	public static final String XMLNS="http://github.com/lindenb/xmake/";
	private static final String TARGET_VARNAME="$@";
	private static final String DEPENDENCIES_VARNAME="$^";
	private static final String DEPENDENCY_VARNAME="$<";
	private static long ID_GENERATOR=1L;
	private final static Logger LOG = Logger.getLogger(XMake.class.getName()); 
	private File workingDirectory = new File( System.getProperty("user.dir"));
	private Context mainContext = new Context();
	private List<Rule> rules = new  ArrayList<Rule>();
	private Map<File,DepFile> depFilesHash = new  HashMap<File,DepFile>();


	public static class EvalException extends Exception
		{
		public EvalException() {
			super();
			}
	
		public EvalException(String message, Throwable cause,
				boolean enableSuppression, boolean writableStackTrace) {
			super(message, cause, enableSuppression, writableStackTrace);
			}
	
		public EvalException(String message, Throwable cause) {
			super(message, cause);
			}
	
		public EvalException(String message) {
			super(message);
			}
		
		public EvalException(Node node,String message) {
			super(toString(node)+" "+message);
			}
		
		public EvalException(Throwable cause) {
			super(cause);
			}
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
		}
	
	public static class StreamConsummer
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

	
	
	public enum Status
			{
			NIL,
			OK,
			DIRTY,
			FATAL
		}

	private class XNode
		{
		XNode child=null;
		XNode next=null;
		public XNode appendChild(XNode c)
			{
			if(child==null)
				{
				child=c;
				}
			else
				{
				XNode curr=this;
				while(curr.next!=null) curr=curr.next;
				curr.next=c;
				}
			return this;
			}
		StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			return evalChildren(sb,ctx);
			}
		
		StringBuilder evalChildren(StringBuilder sb,Context ctx) throws EvalException
			{
			for(XNode c=this.child;c!=null;c=c.next)
				{
				c.eval(sb, ctx);
				}
			return sb;
			}

		
		String eval(Context ctx) throws EvalException
			{
			return eval(new StringBuilder(),ctx).toString();
			}
		List<String> evalToArray(Context ctx) throws EvalException
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
	private class PlainTextNode extends XNode
		{
		private String content;
		PlainTextNode(String content)
			{
			this.content = content;
			}
		@Override
		StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			return sb.append(this.content);
			}
		}

	
	private class ValueOf extends XNode
		{
		private String varName;
		private String defaultValue=null;
		ValueOf(Element root) throws EvalException
			{
			Attr att=root.getAttributeNode("name");
			if(att==null) throw new EvalException(root, "@name missing");
			this.varName=att.getValue();
			att=root.getAttributeNode("default");
			if(att!=null) this.defaultValue=att.getValue();
			}
		@Override
		StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
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
	private class TargetName extends XNode
		{
		Node root;
		TargetName(Node root)
			{
			this.root=root;
			}
		@Override
		StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			XNode v = ctx.get(XMake.DEPENDENCY_VARNAME);
			if(v!=null)
				{
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
		StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
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
	
	private class Prerequisites  extends XNode
		{
		Node root;
		Prerequisites(Node root)
			{
			this.root=root;
			}
		@Override
		StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
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

	private class NotDir  extends XNode
		{
		@Override
		StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			boolean first=true;
			for(String token: XMake.WSPACES.split(evalChildren(new StringBuilder(), ctx).toString()))
				{
				if(token.isEmpty()) continue;
				try {
					File f=new File(token);
					if(!first) sb.append(" ");
					first=false;
					sb.append(f.getName());
					} 
				catch (Exception e) {
					
					}
				}
			return sb;
			}
		}

	private class OnlyDir  extends XNode
		{
		@Override
		StringBuilder eval(StringBuilder sb,Context ctx) throws EvalException
			{
			boolean first=true;
			for(String token: XMake.WSPACES.split(evalChildren(new StringBuilder(), ctx).toString()))
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
					
					}
				}
			return sb;
			}
		}

	
	
	private class Context
		{
		private Context parent=null;
		private Map<String, XNode> variables = new HashMap<String, XNode>();
		
		Context(Context parent)
			{
			this.parent = parent;
			}
		
		Context()
			{
			this(null);
			}
		
		public XNode get(String name)
			{
			XNode v= variables.get(name);
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
				}
			return v;
			}
		
		public void put(String name,XNode variable)
			{
			this.variables.put(
				name,
				variable
				);
			}
		public void put(String name,String variable)
			{
			XNode n=new XNode();
			n.appendChild(new PlainTextNode(variable));
			this.variables.put(
				name,
				n
				);
			}
		
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
			
		@Override
		public String toString()
			{ 
			return keySet().toString();
			}
		}

	public class DepFile
		implements Callable<Integer>
		{	
		Rule rule=null;
		File file;
		Status status = Status.NIL;
		Set<DepFile> prerequisites = new LinkedHashSet<>();
		Future<Integer> returnedValue=null;
		String shellPath="/bin/bash";
		private int ret=-1;


		
		DepFile(File file)
			{
			this(null,file);
			}
		
		DepFile(Rule rule,File file)
			{
			this.rule=rule;
			this.file=file;
			}
		
		void checkForLoops() throws EvalException
			{
			_noLoop(new HashSet<DepFile>());
			}
		private void _noLoop(Set<DepFile> visited) throws EvalException
			{
			if(visited.contains(this))
				{
				throw new EvalException("Circular definition of "+
						this+":"+visited);
				}
			visited.add(this);
			for(DepFile in:this.prerequisites) in._noLoop(visited);
			}
		
		public Set<File> getPrerequisiteFiles() throws EvalException
			{
			Set<File> inputFiles=new LinkedHashSet<>();
			if(this.rule!=null)
				{
				Context ctx=new Context(this.rule.getContext());
				ctx.put(DEPENDENCY_VARNAME, getFile().toString());
				for(String inputf:this.rule.input.evalToArray(ctx))
					{
					File filein = new File(this.rule.getWorkingDirectory(),inputf);
					inputFiles.add(filein);
					}
				}
				
			return inputFiles;
			}
		
		
		public boolean hasDependency(DepFile f)
			{
			for(DepFile dp:this.prerequisites)
				{
				if(dp.equals(f) || dp.hasDependency(f)) return true;
				}
			return false;
			}

		public File getTmpDir()
			{
			return new File("/tmp");
			}

		@Override
		public Integer call() throws Exception
			{
			StreamConsummer stdout=null;
			StreamConsummer stderr=null;
			File tmpFile=null;
			try
				{
				
				Context ctx=new Context(this.rule.getContext());
				ctx.put(TARGET_VARNAME, this.toString());
				
				tmpFile = File.createTempFile("xmake.", ".sh",getTmpDir());
				PrintWriter pw=new PrintWriter(tmpFile);
				pw.println("#/bin/bash");
				pw.append(this.rule.command.eval(ctx));
				pw.flush();
				pw.close();

				
				Process p = new ProcessBuilder().command(this.shellPath,tmpFile.getPath()).start();
				stdout = new StreamConsummer(p.getInputStream(), System.out,"[LOGO]");
				stderr = new StreamConsummer(p.getErrorStream(), System.err,"[LOGE]");
				stdout.start();
				stderr.start();
				ret= p.waitFor();
				stdout.join();
				stderr.join();
				LOG.info("DOne");
				tmpFile.delete();
				return ret;
				}
			catch(Exception err)
				{
				this.ret=-1;
				System.err.println(err.getMessage());
				return -1;
				}
			finally
				{
				stdout=null;
				stderr=null;
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
				if(_isA(n1, "ouput"))
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
		
		public Set<DepFile> getDepFiles()  throws EvalException {
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
	
	public void compile(Document dom) throws EvalException
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
						else if(_isA(e1,"notdir"))
							{
							parent.appendChild(new NotDir());
							}
						else if(_isA(e1,"dir") || _isA(e1,"directory"))
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

		Document xmlMakefile=null;
		try {
			DocumentBuilderFactory dbf=DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			DocumentBuilder db=dbf.newDocumentBuilder();
			
			if(makefileFile.equals("-"))
				{
				LOG.info("Reading <stdin>");
				xmlMakefile =db.parse(System.in);
				}
			else
				{
				LOG.info("Reading "+makefileFile);
				xmlMakefile =db.parse(new File(makefileFile));
				}
			} 
		catch (Exception e)
			{
			LOG.log(Level.SEVERE,"Cannot read input "+makefileFile);
			e.printStackTrace();
			return -1;
			}
		
		compile(xmlMakefile);
		
		//create all outputs
		for(Rule rule : this.rules )
			{
			for(DepFile outDep : rule.getDepFiles())
				{
				DepFile prevDeclaration = this.depFilesHash.get(outDep.getFile()) ;
				if(prevDeclaration!=null )
					{
					if(prevDeclaration.rule!=null)
						{
						LOG.exiting("XMake","compile","Target file defined twice "+outDep+" "+prevDeclaration);
						System.exit(-1);	
						}
					else
						{
						/* backward assign, this 'dep' was defined as 'input' but we didn't know
						 it's rule */
						prevDeclaration.rule = rule;
						/* we use the previous declaration */
						outDep= prevDeclaration;
						}
					}
				else
					{
					this.depFilesHash.put(outDep.getFile(),outDep);
					}
				//create all inputs
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
			df.checkForLoops();
		//check for target without rules
		for(DepFile df:this.depFilesHash.values())
			{
			if(df.rule!=null) continue;
			if(df.exists()) continue;
			LOG.exiting("XMake","instanceMain",
					"No rule to build "+df+" required by "+df.getRequiredBy());
			System.exit(-1);	
			}
		
		//create an array of DepFile, run a topological sort
		List<DepFile> sortedTargets = new ArrayList<>(this.depFilesHash.size());
		for(DepFile dp:this.depFilesHash.values())
			{
			//this is the first target
			if(sortedTargets.isEmpty())
				{
				sortedTargets.add(dp);
				continue;
				}
			//find the right place where to add this file
			int insertIndex = sortedTargets.size()-1;
			// loop until there is no dependency
			while(insertIndex>0 && sortedTargets.get(insertIndex).hasDependency(dp))
				{
				--insertIndex;
				}
			sortedTargets.add(insertIndex, dp);
			}
		try
			{
			ExecutorService executor = Executors.newFixedThreadPool(1);
			CompletionService<Integer> completionService = new ExecutorCompletionService<>(executor );
			/** loop over file, get next pool of indepedant files */
			List<Command> processed = new ArrayList<>();

			while(!sortedTargets.isEmpty())
				{
				File tmpDir=null;
				int idx=0;
				while(idx<sortedTargets.size())
					{
					DepFile dp=sortedTargets.get(idx);
					boolean ok=true;
					for(Command other:agenda)
						{
						if(dp.hasDependency(other.depFile))
							{
							ok=false;
							break;
							}
						}
					if(ok)
						{
						agenda.add(dp);
						Rule rule= dp.rule;
						if(!rule.hasCommand()) continue;

						command.returnedValue = completionService.submit(command);
						sortedTargets.remove(idx);
						}
					else
						{
						++idx;
						}
					}
				}
			executor.shutdown();
			}
		catch(Exception err)
			{
			err.printStackTrace();
			return -1;
			}
		finally
			{
			
			}

		return 0;
		}
	
	public static void main(String args[]) throws Exception
		{
		XMake app = new XMake();
		app.instanceMainWithExit(args); 
		}
	}