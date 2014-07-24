package com.lm.artifex.mupdfdemo;

// Version of MuPDFAlert without enums to simplify JNI
public class MuPDFAlertInternal {
	public final String message;
	public final int iconType;
	public final int buttonGroupType;
	public final String title;
	public int buttonPressed;

	public MuPDFAlertInternal(String aMessage, int aIconType, int aButtonGroupType, String aTitle, int aButtonPressed) {
		message = aMessage;
		iconType = aIconType;
		buttonGroupType = aButtonGroupType;
		title = aTitle;
		buttonPressed = aButtonPressed;
	}

	public MuPDFAlertInternal(MuPDFAlert alert) {
		message = alert.message;
		iconType = alert.iconType.ordinal();
		buttonGroupType = alert.buttonGroupType.ordinal();
		title = alert.message;
		buttonPressed = alert.buttonPressed.ordinal();
	}

	public MuPDFAlert toAlert() {
		return new MuPDFAlert(message, MuPDFAlert.IconType.values()[iconType], MuPDFAlert.ButtonGroupType.values()[buttonGroupType], title, MuPDFAlert.ButtonPressed.values()[buttonPressed]);
	}
}
