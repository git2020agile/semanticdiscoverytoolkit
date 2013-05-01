/*
    Copyright 2013 Semantic Discovery, Inc. (www.semanticdiscovery.com)

    This file is part of the Semantic Discovery Toolkit.

    The Semantic Discovery Toolkit is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    The Semantic Discovery Toolkit is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with The Semantic Discovery Toolkit.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.sd.io;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;
import static org.sd.io.OperatingSystemUtil.OperatingSystem.*;

/**
 * JUnit Tests for the NFSMount class.
 * <p>
 * @author Abe Sanderson
 */
public class TestNFSMount extends TestCase {

  public TestNFSMount(String name) {
    super(name);
  }
  
  public void testGetLocalFile() 
    throws IOException
  {
    String[] inputs = new String[] {
      "\\path\\to\\file.txt",
    };
    String[] expected = new String[] {
      "/mnt/test/path/to/file.txt",
    };
    
    NFSMount mount = new NFSMount("\\\\testunc\\shared\\folder", WIN_8, 
                                  new File("/mnt/test"));
    for(int i = 0; i < inputs.length; i++)
    {
      String in = inputs[i];
      String ex = expected[i];
      
      assertEquals("input at index "+i+" does not match",
                   ex, mount.getLocalFile(in).getAbsolutePath());
    }
  }

  public void testBuildRemotePath() 
    throws IOException
  {
    File[] inputs = new File[] {
      new File("/mnt/test/path/to/file.txt"),
    };
    String[] expected = new String[] {
      "\\\\testunc\\shared\\folder\\path\\to\\file.txt",
    };
    
    NFSMount mount = new NFSMount("\\\\testunc\\shared\\folder", WIN_8, 
                                  new File("/mnt/test"));
    for(int i = 0; i < inputs.length; i++)
    {
      File in = inputs[i];
      String ex = expected[i];
      
      assertEquals("input at index "+i+" does not match",
                   ex, mount.buildRemotePath(in));
    }
  }

  public static Test suite() {
    TestSuite suite = new TestSuite(TestNFSMount.class);
    return suite;
  }

  public static void main(String[] args) {
    junit.textui.TestRunner.run(suite());
  }
}