package com.viaversion.aas.util;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import kotlin.text.StringsKt;

import java.util.*;
import java.util.regex.Pattern;

public class AddressParser {
	public Integer protocol;
	public String viaSuffix;
	public String serverAddress;
	public String viaOptions;
	public Integer port;
	public String username;
	public Boolean online;

	public AddressParser parse(String address) {
		boolean stopOptions = false;
		List<String> optionsParts = new ArrayList<>();
		List<String> serverParts = new ArrayList<>();

		for (String part : Lists.reverse(Arrays.asList(address.split(Pattern.quote("."))))) {
			if (!stopOptions && parseOption(part)) {
				optionsParts.add(part);
				continue;
			}
			stopOptions = true;
			serverParts.add(part);
		}

		serverAddress = String.join(".", Lists.reverse(serverParts));
		viaOptions = String.join(".", Lists.reverse(optionsParts));

		return this;
	}

	public AddressParser parse(String rawAddress, List<String> viaHostName) {
		String address = StringsKt.removeSuffix(rawAddress, ".");

		String suffix = viaHostName.stream()
				.filter(s -> StringsKt.endsWith("." + address, s, false))
				.findAny()
				.orElse(null);

		String suffixRemoved;
		if (suffix != null) {
			suffixRemoved = StringsKt.removeSuffix(address, "." + suffix);
		} else {
			serverAddress = address;
			return this;
		}

		parse(suffixRemoved);
		viaSuffix = suffix;

		return this;
	}

	public boolean parseOption(String part) {
		String option;
		if (part.length() < 2) {
			return false;
		} else if (part.startsWith("_")) {
			option = String.valueOf(part.charAt(1));
		} else if (part.charAt(1) == '_') {
			option = String.valueOf(part.charAt(0));
		} else {
			return false;
		}

		String arg = part.substring(2);
		switch (option.toLowerCase(Locale.ROOT)) {
			case "o": {
				parseOnlineMode(arg);
				break;
			}
			case "p": {
				parsePort(arg);
				break;
			}
			case "u": {
				parseUsername(arg);
				break;
			}
			case "v": {
				parseProtocol(arg);
				break;
			}
		}

		return true;
	}

	public void parsePort(String arg) {
		port = Ints.tryParse(arg);
	}

	public void parseUsername(String arg) {
		if (arg.length() > 16) throw new IllegalArgumentException("Invalid username");
		username = arg;
	}

	public void parseOnlineMode(String arg) {
		online = null;
		if (StringsKt.startsWith(arg, "t", true)) {
			online = true;
		} else if (StringsKt.startsWith(arg, "f", true)) {
			online = false;
		}
	}

	public void parseProtocol(String arg) {
		protocol = Ints.tryParse(arg);
		if (protocol == null) {
			ProtocolVersion ver = ProtocolVersion.getClosest(arg.replace("_", "."));
			if (ver != null) protocol = ver.getVersion();
		}
	}
}
