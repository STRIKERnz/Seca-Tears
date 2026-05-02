package com.strikernz.secatears;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SecaTearsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SecaTearsPlugin.class);
		RuneLite.main(args);
	}
}
