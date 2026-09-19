package com.dash;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TextSize
{
	SMALL("Small", 12f),
	NORMAL("Normal", 14f),
	LARGE("Large", 17f);

	private final String label;
	private final float points;

	@Override
	public String toString()
	{
		return label;
	}
}
