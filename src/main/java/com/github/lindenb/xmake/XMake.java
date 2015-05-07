package com.github.lindenb.xmake;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	
	private class MainContext
		extends DefaultContext
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
	
	
	
	public void instanceMain(String args[]) throws EvalException
		{
		try {
			DocumentBuilderFactory dbf=DocumentBuilderFactory.newInstance();
			DocumentBuilder db=dbf.newDocumentBuilder();
			this.xmlMakefile =db.parse(new File(args[0]));
			} 
		catch (Exception e) {
			e.printStackTrace();
			return;
			}
		
		compile();
		for(Rule rule : this.rules )
			{
			List<String> outputs = rule.getOutputs(mainContext);
			
			
			
			for(String input :   rule.getInputs(this.mainContext) )
				{
				DepFile depFile = this.depFilesHash.get(input);
				if( depFile == null)
					{
					LOG.info("Creating target file "+input);
					depFile = new DepFile(input);
					this.depFilesHash.put(input,depFile);
					}
				if( depFile.rule != null )
					{
					LOG.warning("Target file defined twice "+input);
					System.exit(-1);	
					}
				depFile.rule = rule;
				for(String output :   outputs )
					{
					DepFile outDep = this.depFilesHash.get(output);
					if( outDep== null)
						{
						outDep = new DepFile(output);
						this.depFilesHash.put(output,outDep);
						}
					depFile.prerequisites.add(outDep);
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
		LOG.info(L.toString());

		
		}
	
	public static void main(String args[]) throws Exception
		{
		XMake app = new XMake();
		app.instanceMain(args); 
		}
	}