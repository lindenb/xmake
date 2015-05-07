package com.github.lindenb.xmake;

import java.util.List;

public abstract class Rule extends AbstractEval
	{
	public static final String TARGET_VARNAME="$@";
	public static final String DEPENDENCIES_VARNAME="$^";
	public static final String DEPENDENCY_VARNAME="$<";
	
	
	public abstract List<String> getInputs(Context ctx) throws EvalException;
	public abstract List<String> getOutputs(Context ctx) throws EvalException;
	}
