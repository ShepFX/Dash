package com.dash;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum DashMode
{
	ALWAYS("Always"),
	WHEN_UNFOCUSED("Only when unfocused"),
	HOTKEY_ONLY("Only with hotkey");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
