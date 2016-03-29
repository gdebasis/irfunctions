/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simfuns;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject.Kind;
import retriever.TrecDocRetriever;

/**
 *
 * @author Debasis
 */

class JavaSourceFromString extends SimpleJavaFileObject {
  final String code;

  JavaSourceFromString(String name, String code) {
    super(URI.create("string:///" + name.replace('.','/') + Kind.SOURCE.extension),Kind.SOURCE);
    this.code = code;
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) {
    return code;
  }
}

public class SimilarityFunctionCompiler {
    String className;
    JavaFileObject javaFileObj;
    JavaCompiler compiler;
    DiagnosticCollector<JavaFileObject> diagnostics;
    Properties prop;
    String workDir;
    String simDir;
    File workDirF;
    final static String PackageName = "simfuns";
    final static String PackageInclusion = "package " + PackageName + ";\n";
    final static String MethodNameToInvoke = "op";
    
    public SimilarityFunctionCompiler(String propFileName, String className, String code) throws IOException {
        prop = new Properties();
        prop.load(new FileReader(propFileName));
        
        workDir = prop.getProperty("workdir");
        simDir = prop.getProperty("simdir");
        workDirF = new File(workDir);
        
        this.className = className;
        javaFileObj = new JavaSourceFromString(className, PackageInclusion + code);
        compiler = ToolProvider.getSystemJavaCompiler();
        diagnostics = new DiagnosticCollector<JavaFileObject>();
    }

    public boolean compile() {
        Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(javaFileObj);
        
        List<String> optionList = new ArrayList<String>();
        //String cp = System.getProperty("java.class.path");
            //+ File.pathSeparatorChar + simDir; // add the workdir
        
        //optionList.addAll(Arrays.asList("-classpath", cp));
        String[] options = new String[] { "-d", workDir };
        optionList.addAll(Arrays.asList(options));
        
        CompilationTask task = compiler.getTask(null, null, diagnostics,
                optionList, null, compilationUnits);

        boolean success = task.call();
        return success;
    }
    
    public String getCompilerMsg() {
        StringBuffer compilerMsg = new StringBuffer();
        
        for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
            compilerMsg.append(diagnostic.getMessage(null));
        }
        return compilerMsg.toString();        
    }
    
    public String execute(TrecDocRetriever retriever) {
        Integer retval = null;
        try {
            Object testObj = null;
            URL[] cp = {workDirF.toURI().toURL()};
            URLClassLoader urlcl = new URLClassLoader(cp);
            Class<?> clazz = urlcl.loadClass(PackageName + "." + className);
            Constructor ctor = clazz.getConstructor();
            try {
                testObj = ctor.newInstance();
            }
            catch (InstantiationException | IllegalArgumentException ex) {
                ex.printStackTrace();
            }
            retval = (Integer)(clazz
                .getDeclaredMethod(MethodNameToInvoke, int.class, int.class)
                .invoke(testObj, 2, 3));
        }
        catch (ClassNotFoundException e) {
            return("Class not found: " + e);
        }
        catch (NoSuchMethodException e) {
            return("No such method: " + e);
        }
        catch (IllegalAccessException e) {
            return("Illegal access: " + e);
        }
        catch (InvocationTargetException e) {
            return("Invocation target: " + e);
        }
        catch (MalformedURLException e) {
            return("Invocation target: " + e);                
        }
        catch (Exception ex) {
            return ("Unexpected exception: " + ex);
        }
        return "Executed similarity function " + className + ", result = " + retval;
    }   
}
