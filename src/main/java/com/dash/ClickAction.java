package com.dash;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum ClickAction
{
	HIDE_DASH("Hide Dash"),
	NOTHING("Nothing");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
