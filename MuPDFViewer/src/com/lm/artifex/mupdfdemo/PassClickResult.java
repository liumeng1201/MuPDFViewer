package com.lm.artifex.mupdfdemo;

public class PassClickResult {
	public final boolean changed;

	public PassClickResult(boolean _changed) {
		changed = _changed;
	}

	public void acceptVisitor(PassClickResultVisitor visitor) {
	}
}
