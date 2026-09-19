package com.dash;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Corner
{
	TOP_LEFT("Top left", true, true),
	TOP_RIGHT("Top right", true, false),
	BOTTOM_LEFT("Bottom left", false, true),
	BOTTOM_RIGHT("Bottom right", false, false);

	private final String label;
	private final boolean top;
	private final boolean left;

	@Override
	public String toString()
	{
		return label;
	}
}
