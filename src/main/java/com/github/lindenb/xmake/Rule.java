package com.github.lindenb.xmake;

import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class Rule extends AbstractEval
	{
	public static final String TARGET_VARNAME="$@";
	public static final String DEPENDENCIES_VARNAME="$^";
	public static final String DEPENDENCY_VARNAME="$<";

	private Context ctx;
	private Element root;
	public Rule(Element root,Context ctx)
		{
		this.root=root;
		this.ctx=ctx;
		}
	
	public Context getContext()
		{
		return ctx;
		}
	
	private List<String> _get_element_list(String name,Context cx) throws EvalException {
		for(Node n1=this.root.getFirstChild();
			n1!=null;
			n1=n1.getNextSibling()
			)
			{
			if(n1.getNodeType()!=Node.ELEMENT_NODE)
				{
				_assertBlank(n1);
				continue;
				}
			if(Element.class.cast(n1).getNodeName().equals(name))
				{	
				Context ctx=new Context(cx);
				ctx.put(CONSIDER_TEXT);
				return _evalToStringList(ctx, Element.class.cast(n1));
				}
			}
		return Collections.emptyList();
		}
	
	public List<String> getInputs() throws EvalException {
		return _get_element_list("input",getContext());
		}
	public List<String> getOutputs() throws EvalException {
		return _get_element_list("output",getContext());
		}
	
	protected Element getCommandElement() throws EvalException 
		{
		for(Node n1=this.root.getFirstChild();
			n1!=null;
			n1=n1.getNextSibling()
			)
			{
			if(n1.getNodeType()!=Node.ELEMENT_NODE)
				{
				_assertBlank(n1);
				continue;
				}
			if(Element.class.cast(n1).getNodeName().equals("command"))
				{
				return Element.class.cast(n1);
				}
			}
		return null;
		}
	
	public boolean hasCommand() throws EvalException 
		{
		return getCommandElement()!=null;
		}
	
	@Override
	public Appendable eval(Context ctx, Appendable w) throws EvalException {
		if(!hasCommand()) return w;
		Context ctx2=new Context(ctx);
		ctx2.put(CONSIDER_TEXT);
		return _childrenOf(ctx2,w,getCommandElement());
		}
	

	}
