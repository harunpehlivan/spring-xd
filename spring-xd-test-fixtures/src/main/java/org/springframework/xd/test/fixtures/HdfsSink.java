/*
 * Copyright 2013-2014 the original author or authors.
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

package org.springframework.xd.test.fixtures;

import org.springframework.util.Assert;


/**
 * A test fixture that allows testing of the 'hdfs' sink module.
 *
 * @author Glenn Renfro
 */
public class HdfsSink extends AbstractModuleFixture {


	public static final String DEFAULT_FILE_NAME = "ACCTEST";

	public static final String DEFAULT_DIRECTORY = "/xd/acceptancetest";

	String fileName;

	String directoryName;

	/**
	 * Initializes a hdfs file sink fixture;
	 *
	 * @param directoryName The directory to write the file on the hdfs.
	 * @param fileName The name of file to write on the hdfs.
	 */
	public HdfsSink(String directoryName, String fileName) {
		Assert.hasText(directoryName, "directoryName must not be empty nor null");
		Assert.hasText(fileName, "fileName must not be empty nor null");

		this.fileName = fileName;
		this.directoryName = directoryName;
	}

	/**
	 * Returns an instance of the HdfsSink using defaults.
	 * 
	 * @return instance of the HdfsSink
	 */
	public static HdfsSink withDefaults() {
		return new HdfsSink(DEFAULT_DIRECTORY, DEFAULT_FILE_NAME);
	}

	/**
	 * Renders the DSL for this fixture.
	 */
	@Override
	protected String toDSL() {
		return String.format("hdfs --directory=%s --fileName=%s ", directoryName, fileName);
	}

}
