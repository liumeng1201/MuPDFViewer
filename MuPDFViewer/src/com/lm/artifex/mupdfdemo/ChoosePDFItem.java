package com.lm.artifex.mupdfdemo;

public class ChoosePDFItem {
	enum Type {
		PARENT, DIR, DOC, PDF, IMG, DOCOTHER
	}

	final public Type type;
	final public String name;

	public ChoosePDFItem (Type t, String n) {
		type = t;
		name = n;
	}
}