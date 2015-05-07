package com.github.lindenb.xmake;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public abstract class AbstractEval implements Eval
	{
	private final static Logger LOG = Logger.getLogger(XMake.class.getName()); 
	// http://stackoverflow.com/questions/4731055

	private static final Pattern WSPACES=Pattern.compile("[\\s]+");
	protected static final Variable CONSIDER_TEXT = new SystemVariable("\0\0\0xmake.ignore.text");
	
	protected AbstractEval()
		{
		
		}
	
	protected Appendable _childrenOf(Context ctx,Appendable w,Node root) throws EvalException
		{
		for(Node n1=root.getFirstChild();
				 n1!=null;n1=n1.getNextSibling()
				)
			{
			switch(n1.getNodeType())
				{
				case Node.ELEMENT_NODE:
					{
					Element e1 = Element.class.cast(n1);
					_element(ctx,w,e1);
					break;
					}
				case Node.TEXT_NODE:
					{
					Variable considerText = ctx.get(CONSIDER_TEXT.getName());
					if(CONSIDER_TEXT==considerText)
						{
						_text(ctx, w, Text.class.cast(n1));
						}
					else
						{
						_assertBlank(n1);
						}
					break;
					}
				case Node.DOCUMENT_NODE:
					{
					_childrenOf(ctx,w,Document.class.cast(n1));
					break;
					}
				case Node.COMMENT_NODE:break;
				default: throw new EvalException();
				}
			
			}
		return w;
		}
	
	protected void _element(Context ctx,Appendable w,Element root) throws EvalException
		{
		String name=root.getNodeName();
		
		if(name.equals("addsuffix"))
			{
			_addsuffix(ctx, w, root);
			}
		else if(name.equals("addprefix"))
			{
			_addprefix(ctx, w, root);
			}
		else if(name.equals("choose"))
			{
			_choose(ctx, w, root);
			}
		else if(name.equals("define"))
			{
			_define(ctx, w, root);
			}
		else if(name.equals("dependencies"))
			{
			_dependencies(ctx, w, root);
			}
		else if(name.equals("dependency"))
			{
			_dependency(ctx, w, root);
			}
		else if(name.equals("error"))
			{
			_error(ctx, w, root);
			}
		else if(name.equals("foreach"))
			{
			_forEach(ctx, w, root);
			}
		else if(name.equals("warning"))
			{
			_warning(ctx, w, root);
			}
		else if(name.equals("normalize-space"))
			{
			_normalize_space(ctx, w, root);
			}
		else if(name.equals("rule"))
			{
			_rule(ctx, w, root);
			}
		else if(name.equals("target"))
			{
			_target(ctx, w, root);
			}
		else if(name.equals("variable"))
			{
			_variable(ctx, w, root);
			}
		else
			{
			throw new EvalException("Unknown Element" +name);
			}
		}
	
	protected void _variable(Context cx,Appendable w,Element root) throws EvalException
		{
		Attr att=root.getAttributeNode("name");
		if(att==null) throw new EvalException();
		LOG.info("eval $"+att.getValue());
		Variable variable = cx.get(att.getValue());
		LOG.info("$"+att.getValue()+" "+variable+"");
		if(variable==null)
			{
			LOG.warning("NOT FOUND "+att+ " in "+cx);
			throw new EvalException();
			//return;
			}
		_variable(cx,w,variable);
		}
	protected void _variable(Context cx,Appendable w,Variable variable) throws EvalException
		{
		if(variable==null) return; 
		switch(variable.getType())
			{
			case DOM:
				for(Node n: DefaultVariable.class.cast(variable).nodes())
					{
					LOG.info(n.getNodeName());
					_childrenOf(cx, w, n);
					}
				break;
			case SYSTEM:
				{	
				try {
					w.append(SystemVariable.class.cast(variable).getValue());
					}
				catch (Exception e) {
					throw new EvalException(e);
					}
				break;
				}
			default: throw new EvalException();
			}
		}
	
	protected void _addsuffix(Context cx,Appendable w,Element root) throws EvalException
		{
		Attr att=root.getAttributeNode("suffix");
		if(att==null) throw new EvalException();
		try
			{
			boolean first=true;
			for(String t: _evalToStringList(cx, root))
				{
				if(!first) w.append(" ");
				w.append(t);
				w.append(att.getValue());
				first=false;
				}
			}
		catch(IOException e)
			{
			throw new EvalException(e);
			}
		}

	protected void _addprefix(Context cx,Appendable w,Element root) throws EvalException
		{
		Attr att=root.getAttributeNode("prefix");
		if(att==null) throw new EvalException();
		try
			{
			boolean first=true;
			for(String t: _evalToStringList(cx, root))
				{
				if(!first) w.append(" ");
				w.append(att.getValue());
				w.append(t);
				first=false;
				}
			}
		catch(IOException e)
			{
			throw new EvalException(e);
			}
		}
	
	
	protected void _dependencies(Context cx,Appendable w,Element root) throws EvalException
		{
		Variable v=cx.get(Rule.DEPENDENCIES_VARNAME);
		if(v!=null) _variable(cx,w,v);
		}
	
	protected void _dependency(Context cx,Appendable w,Element root) throws EvalException
		{
		Variable v=cx.get(Rule.DEPENDENCY_VARNAME);
		if(v!=null) _variable(cx,w,v);
		}

	
	protected void _target(Context cx,Appendable w,Element root) throws EvalException
		{
		Variable v=cx.get(Rule.TARGET_VARNAME);
		if(v!=null) _variable(cx,w,v);
		}
	
	protected void _normalize_space(Context cx,Appendable w,Element root) throws EvalException
		{
		try
			{
			w.append(WSPACES.matcher(_evalToString(cx, root)).replaceAll(" ").trim());
			}
		catch(IOException e)
			{
			throw new EvalException(e);
			}
		}
	
	protected void _warning(Context cx,Appendable w,Element root) throws EvalException
		{
		LOG.warning(_evalToString(cx, root));
		}
	
	protected void _error(Context cx,Appendable w,Element root) throws EvalException
		{
		String msg=_evalToString(cx, root);
		LOG.log(Level.SEVERE,msg);
		throw new EvalException(msg);
		}

	protected void _forEach(Context cx,Appendable w,Element root) throws EvalException
		{
		List<String> tokens = new ArrayList<>();
		String varName="";
		for(String t: tokens)
			{
			DefaultContext ctx=new DefaultContext(cx);
			ctx.put(new SystemVariable(varName, t));
			_childrenOf(cx,w,root);
			}
		}
	protected void _text(Context cx,Appendable w,Text txt)throws EvalException
		{
		try {
			w.append(txt.getTextContent());
		} catch (Exception e) {
			throw new EvalException(e);
		}
		}
	
	protected void _define(Context cx,Appendable w,Element root)throws EvalException
		{
		Attr att=root.getAttributeNode("name");
		if(att==null) throw new EvalException("@name missing");
		LOG.info("creating context "+att.getValue());
		DefaultVariable v= new DefaultVariable(cx,att.getValue(),_list(root));
		cx.put(v);
		LOG.info(v.toString());
		}
	
	protected void _choose(Context cx,Appendable w,Element root) throws EvalException
		{
		Element otherwise=null;
		DefaultContext ctx=new DefaultContext(cx);
		for(Node n1=root.getFirstChild();
				 n1!=null;n1=n1.getNextSibling()
				)
			{
			if(n1.getNodeType()!=Node.ELEMENT_NODE)
				{
				_assertBlank(n1);
				continue;
				}
			Element e1 = Element.class.cast(n1);
			String name = e1.getNodeName();
			if(name.equals("when"))
				{
				if(otherwise!=null) throw new EvalException("Otherwise defined before test");
				if( _when(ctx,w,e1) ) break;
				}
			else if(name.equals("otherwise"))
				{
				if(otherwise!=null) throw new EvalException("Otherwise already defined");
				otherwise = e1;
				
				}
			else
				{
				throw new EvalException("undefined");
				}
			}
		}
	
	protected boolean _when(Context cx,Appendable w,Element root) throws EvalException
		{
		Element test=null;
		Element then=null;
		DefaultContext ctx=new DefaultContext(cx);
		for(Node n1=root.getFirstChild();
				 n1!=null;n1=n1.getNextSibling()
				)
			{
			if(n1.getNodeType()!=Node.ELEMENT_NODE)
				{
				_assertBlank(n1);
				continue;
				}
			Element e1 = Element.class.cast(n1);
			String name = e1.getNodeName();
			if(name.equals("test"))
				{
				if(test!=null) throw new EvalException("test defined twice");
				test=e1;
				}
			else if(name.equals("then"))
				{
				if(then!=null) throw new EvalException("then defined twice");
				then=e1;
				}
			else
				{
				throw new EvalException("undefined");
				}
			}
		boolean ok = _test(ctx,w,test);
		if(ok && then!=null)
			{
			_childrenOf(ctx, w, then);
			}
		return ok;
		}
	
	protected boolean _test(Context cx,Appendable w,Element root) throws EvalException
		{
		DefaultContext ctx=new DefaultContext(cx);
		StringBuilder sb=new StringBuilder();
		_childrenOf(ctx,sb,root);
		return !sb.toString().trim().isEmpty();
		}
	
	protected void foundRuleEvent(Rule rule) throws EvalException
		{
		throw new EvalException("Unexpected Rule");
		}
	
	protected void _rule(Context cx,Appendable w,Element root) throws EvalException
		{
		DefaultRule rule=new DefaultRule(root);
		foundRuleEvent(rule);
		}
	
	protected static void _assertBlank(Node root) throws EvalException
		{
		if(root==null ) return;
		String s=root.getTextContent();
		if(s.trim().isEmpty()) return;
		throw new EvalException();
		}	
	
	protected static List<Node> _list(Node root)
		{
		List<Node> L = new ArrayList<>();
		for(Node n1=root.getFirstChild();
				 n1!=null;n1=n1.getNextSibling()
				)
			{
			L.add(n1);
			}
		return L;
		}
	
	protected String _evalToString(Context cx,Element root)throws EvalException
		{
		StringBuilder sb=new StringBuilder();
		_childrenOf(cx,sb,root);
		return sb.toString();
		}

	protected List<String> _evalToStringList(Context cx,Element root)throws EvalException
		{
		 List<String> L=new ArrayList<>();
		 for(String s:WSPACES.split(_evalToString(cx,root)))
		 	{
			if(s.isEmpty()) continue;
			L.add(s);
		 	}
		return L;
		}
	
	private static class MergingContext
		implements Context
		{
		Context primary;
		Context secondary;
		MergingContext(Context primary,Context secondary)
			{
			this.primary=primary;
			this.secondary=secondary;
			}
		@Override
		public Variable get(String name)
			{
			Variable v=primary.get(name);
			return v==null?secondary.get(name):v;
			}
		@Override
		public void put(Variable b) {
			this.primary.put(b);
			}
		
		}
	
	
	}
