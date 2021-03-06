/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.shell.command;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;


/**
 * {@link UserInput} that uses Standard in and out.
 * 
 * @author Eric Bottard
 */
@Component
public class ConsoleUserInput implements UserInput {

	/**
	 * Loops until one of the {@code options} is provided. Pressing return is equivalent to returning
	 * {@code defaultValue}.
	 */
	@Override
	public String prompt(String prompt, String defaultValue, String... options) {
		List<String> optionsAsList = Arrays.asList(options);
		InputStreamReader console = new InputStreamReader(System.in);
		String answer;
		do {
			answer = "";
			System.out.format("%s %s: ", prompt, optionsAsList);
			try {
				for (char c = (char) console.read(); !(c == '\n' || c == '\r'); c = (char) console.read()) {
					System.out.print(c);
					answer += c;
				}
				System.out.println();
			}
			catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
		while (!optionsAsList.contains(answer) && !"".equals(answer));
		return "".equals(answer) && !optionsAsList.contains("") ? defaultValue : answer;
	}
}
