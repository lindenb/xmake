package com.github.lindenb.xmake;

import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DefaultRule extends Rule
	{
	private Element root;
	public DefaultRule(Element root)
		{
		this.root=root;
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
				DefaultContext ctx=new DefaultContext(cx);
				ctx.put(CONSIDER_TEXT);
				return _evalToStringList(ctx, Element.class.cast(n1));
				}
			}
		return Collections.emptyList();
		}
	
	@Override
	public List<String> getInputs(Context ctx) throws EvalException {
		return _get_element_list("input",ctx);
		}
	@Override
	public List<String> getOutputs(Context ctx) throws EvalException {
		return _get_element_list("output",ctx);
		}
	
	@Override
	public Appendable eval(Context ctx, Appendable w) throws EvalException {
		return _childrenOf(ctx,w,this.root);
		}
	
	
	}
