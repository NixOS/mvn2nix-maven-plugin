/*
 * Copyright (c) 2022 Benjamin Asbach
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.nixos.mvn2nix;

import java.net.URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class URIBuilderTest {

	@Test
	public void shouldBuildURI() throws Exception {
		assertEquals(
			new URI("https://repo.maven.apache.org/maven2/org/assertj/assertj-core/3.16.1/assertj-core-3.16.1.jar"),
			URIBuilder.build("https://repo.maven.apache.org/maven2", new URI("org/assertj/assertj-core/3.16.1/assertj-core-3.16.1.jar"))
		);
	}
	
	@Test
	public void shouldNotDoubleSlashWhenBaseEndsWithSlash() throws Exception {
		assertEquals(
			new URI("https://repo.maven.apache.org/maven2/org/assertj/assertj-core/3.16.1/assertj-core-3.16.1.jar"),
			URIBuilder.build("https://repo.maven.apache.org/maven2/", new URI("org/assertj/assertj-core/3.16.1/assertj-core-3.16.1.jar"))
		);
	}
	
	@Test
	public void shouldNotDoubleSlashWhenPathSuffixStartsWithSlash() throws Exception {
		assertEquals(
			new URI("https://repo.maven.apache.org/maven2/org/assertj/assertj-core/3.16.1/assertj-core-3.16.1.jar"),
			URIBuilder.build("https://repo.maven.apache.org/maven2", new URI("/org/assertj/assertj-core/3.16.1/assertj-core-3.16.1.jar"))
		);
	}
}	
