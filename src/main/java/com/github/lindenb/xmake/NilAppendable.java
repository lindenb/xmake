package com.github.lindenb.xmake;

import java.io.IOException;

public class NilAppendable implements Appendable {

	@Override
	public Appendable append(CharSequence csq) throws IOException {
		return this;
	}

	@Override
	public Appendable append(CharSequence csq, int start, int end)
			throws IOException {
		return this;
	}

	@Override
	public Appendable append(char c) throws IOException {
		return this;
	}

}
