package ai.learnharness.android;
import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;
public class BookDocumentsDeviceTest {
    Context context;List<File> files=new ArrayList<>();
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();}
    @After public void cleanup(){for(File f:files)f.delete();}
    private File file(String suffix)throws Exception{File f=File.createTempFile("source-qa-",suffix,context.getCacheDir());files.add(f);return f;}
    @Test public void grepTextKeepsOnlyBoundedMatchingContext()throws Exception{
        File f=file(".txt");StringBuilder body=new StringBuilder();for(int i=1;i<=200;i++)body.append(i==150?"needle beta evidence":"Ordinary sentence "+i).append('\n');try(OutputStream out=new FileOutputStream(f)){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
        var result=BookDocuments.search(context,Uri.fromFile(f),"Text",BookDocuments.pattern("needle",false),"2",12);assertEquals(1,result.hits().size());assertTrue(result.hits().get(0).locator().contains("101-200"));assertTrue(result.hits().get(0).text().contains("needle beta"));assertTrue(result.hits().get(0).text().length()<1401);assertEquals(1,result.units());
    }
    @Test public void readsPdfByPageAndPreservesLocator()throws Exception{
        File f=file(".pdf");PdfDocument pdf=new PdfDocument();try{
            for(int i=1;i<=2;i++){PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(500,500,i).create());Paint paint=new Paint();paint.setTextSize(18);page.getCanvas().drawText(i==1?"First ordinary page":"Mitochondria produce ATP",20,60,paint);pdf.finishPage(page);}try(OutputStream out=new FileOutputStream(f)){pdf.writeTo(out);}
        }finally{pdf.close();}
        var result=BookDocuments.search(context,Uri.fromFile(f),"PDF",BookDocuments.pattern("ATP",false),"2",12);assertEquals(1,result.hits().size());assertTrue(result.hits().get(0).locator().startsWith("Page 2"));assertTrue(BookDocuments.readUnit(context,Uri.fromFile(f),"PDF",2).contains("ATP"));
    }
    private void entry(ZipOutputStream zip,String name,String text)throws Exception{zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    @Test public void epubUsesSpineOrderAndDeletesTemporaryRawWorkingFile()throws Exception{
        File f=file(".epub");try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(f))){
            entry(zip,"META-INF/container.xml","<container><rootfiles><rootfile full-path=\"OEBPS/book.opf\"/></rootfiles></container>");
            entry(zip,"OEBPS/book.opf","<package><manifest><item id=\"a\" href=\"a.xhtml\"/><item id=\"b\" href=\"b.xhtml\"/></manifest><spine><itemref idref=\"b\"/><itemref idref=\"a\"/></spine></package>");
            entry(zip,"OEBPS/a.xhtml","<html><body><h1>Later chapter</h1><p>Unique mitochondria evidence.</p></body></html>");entry(zip,"OEBPS/b.xhtml","<html><body><p>Earlier chapter.</p></body></html>");
        }
        Set<String> before=new HashSet<>(Arrays.asList(context.getCacheDir().list()));
        var result=BookDocuments.search(context,Uri.fromFile(f),"EPUB",BookDocuments.pattern("mitochondria",false),"2",12);assertEquals(1,result.hits().size());assertTrue(result.hits().get(0).locator().startsWith("Chapter 2"));
        for(String name:context.getCacheDir().list())if(!before.contains(name))assertFalse(name.startsWith("book-read-"));
    }
    @Test public void noMatchDoesNotProduceSourceText()throws Exception{File f=file(".txt");try(OutputStream out=new FileOutputStream(f)){out.write("unrelated".getBytes(StandardCharsets.UTF_8));}assertTrue(BookDocuments.search(context,Uri.fromFile(f),"Text",BookDocuments.pattern("needle",false),"",12).hits().isEmpty());}
}
