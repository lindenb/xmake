package com.github.lindenb.xmake;

import java.io.IOException;

public interface Eval {
public Appendable eval(Context ctx,Appendable w) throws EvalException;
}
