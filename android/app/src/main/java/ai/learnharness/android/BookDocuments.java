package ai.learnharness.android;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.graphics.pdf.PdfRenderer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.jsoup.Jsoup;
import com.google.re2j.Pattern;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;

/** Original document access on demand. No extracted full-text store or import-time indexing. */
public final class BookDocuments {
    public record Hit(String locator,String text){}
    public record Result(List<Hit> hits,int units,boolean limited){
        public String summary(){return units+" pages/chapters/line blocks checked"+(limited?" · search limit reached; narrow the scope or continue later":"");}
    }
    public static Pattern pattern(String query,boolean regex){
        if(query.length()>500)throw new IllegalArgumentException("Keep the search below 500 characters.");
        return Pattern.compile(regex?query:Pattern.quote(query),Pattern.CASE_INSENSITIVE);
    }
    public static Pattern keywords(String text){
        Set<String> stop=new HashSet<>(Arrays.asList("the","and","that","this","with","from","about","what","how","does","would","could","please","explain","have","into","your","their","they","them","then","than","when","where","which","will","just","help","want","learn","teach","confidence","selected","slider"));
        LinkedHashSet<String> words=new LinkedHashSet<>();java.util.regex.Matcher m=java.util.regex.Pattern.compile("[\\p{L}\\p{N}]{3,}").matcher(text.toLowerCase(Locale.ROOT));while(m.find() && words.size()<16)if(!stop.contains(m.group()))words.add(Pattern.quote(m.group()));
        return words.isEmpty()?Pattern.compile("a\\A"):Pattern.compile(String.join("|",words),Pattern.CASE_INSENSITIVE);
    }
    public static int[] scope(String value)throws IOException{
        if(value.trim().isEmpty())return new int[]{1,Integer.MAX_VALUE};
        if(!value.trim().matches("[0-9]{1,6}(-[0-9]{1,6})?"))throw new IOException("Use one page/chapter number or a range such as 10-30. Leave blank to search all.");
        String[] parts=value.trim().split("-");int first=Integer.parseInt(parts[0]),last=parts.length==1?first:Integer.parseInt(parts[1]);if(first<1 || last<first)throw new IOException("Enter a valid page or chapter range.");return new int[]{first,last};
    }
    private interface Visitor {boolean text(String locator,String text)throws Exception;}
    public static Result search(Context context,Uri uri,String kind,Pattern query,String range,int limit)throws Exception{
        List<Hit> hits=new ArrayList<>();int[] count={0};boolean[] limited={false};long deadline=System.nanoTime()+20_000_000_000L;
        visit(context,uri,kind,scope(range),(location,text)->{
            count[0]++;
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Document search cancelled.");
            if(System.nanoTime()>deadline || count[0]>2000){limited[0]=true;return false;}
            String[] lines=text.split("\\R");
            for(int line=0;line<lines.length;line++)if(query.matcher(lines[line]).find()){
                StringBuilder excerpt=new StringBuilder();for(int j=Math.max(0,line-2);j<=Math.min(lines.length-1,line+2);j++){if(excerpt.length()>0)excerpt.append('\n');excerpt.append(lines[j]);}
                String snippet=excerpt.toString();if(snippet.length()>1400){com.google.re2j.Matcher match=query.matcher(snippet);int at=match.find()?match.start():0;int start=Math.max(0,at-300);snippet=snippet.substring(start,Math.min(snippet.length(),start+1400));}
                hits.add(new Hit(location+" · line "+(line+1),snippet));line+=2;
                if(hits.size()>=limit){limited[0]=true;return false;}
            }
            return true;
        });
        return new Result(hits,count[0],limited[0]);
    }
    public static String readUnit(Context context,Uri uri,String kind,int unit)throws Exception{
        StringBuilder out=new StringBuilder();visit(context,uri,kind,new int[]{unit,unit},(where,text)->{if(text.trim().isEmpty())return false;out.append(where).append("\n\n").append(text,0,Math.min(text.length(),16000));if(text.length()>16000)out.append("\n[Page/section text truncated]");return false;});
        if(out.length()==0)throw new IOException("This page/chapter is empty or outside the document. Scanned PDFs may require OCR.");return out.toString();
    }
    private static void visit(Context context,Uri uri,String kind,int[] scope,Visitor visitor)throws Exception{
        if(kind.equals("PDF")){
            if(Build.VERSION.SDK_INT>=35){
                try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(uri,"r")){
                    if(fd==null)throw new IOException("Document is unavailable. Select it again from Files.");
                    try(PdfRenderer pdf=new PdfRenderer(fd)){
                        for(int i=scope[0]-1;i<Math.min(pdf.getPageCount(),scope[1]);i++){
                            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Search cancelled.");
                            try(PdfRenderer.Page page=pdf.openPage(i)){
                                StringBuilder text=new StringBuilder();for(var part:page.getTextContents()){if(text.length()+part.getText().length()>1_000_000)throw new IOException("This PDF page contains too much text.");text.append(part.getText()).append('\n');}
                                if(!visitor.text("Page "+(i+1),text.toString()))break;
                            }
                        }
                    }
                }return;
            }
            // Older Android versions need a short-lived raw working file; extracted text is never stored.
            File working=workingFile(context,uri);try{
                PDFBoxResourceLoader.init(context.getApplicationContext());
                try(PDDocument pdf=PDDocument.load(working,"",MemoryUsageSetting.setupTempFileOnly().setTempDir(context.getCacheDir()))){
                    if(!pdf.getCurrentAccessPermission().canExtractContent())throw new IOException("This PDF does not allow text extraction.");
                    PDFTextStripper text=new PDFTextStripper();text.setSortByPosition(true);
                    for(int i=scope[0];i<=Math.min(pdf.getNumberOfPages(),scope[1]);i++){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Search cancelled.");text.setStartPage(i);text.setEndPage(i);if(!visitor.text("Page "+i,text.getText(pdf)))break;}
                }
            }finally{working.delete();}return;
        }
        if(kind.equals("EPUB")){
            File working=workingFile(context,uri);try(ZipFile zip=new ZipFile(working)){
                org.w3c.dom.Document container=xml(entry(zip,"META-INF/container.xml",1_000_000));NodeList roots=container.getElementsByTagNameNS("*","rootfile");if(roots.getLength()==0)throw new IOException("This is not a readable EPUB.");String path=((Element)roots.item(0)).getAttribute("full-path");
                org.w3c.dom.Document pkg=xml(entry(zip,path,2_000_000));String base=path.contains("/")?path.substring(0,path.lastIndexOf('/')+1):"";Map<String,String> manifest=new HashMap<>();NodeList items=pkg.getElementsByTagNameNS("*","item");for(int i=0;i<items.getLength();i++){Element item=(Element)items.item(i);manifest.put(item.getAttribute("id"),item.getAttribute("href"));}
                NodeList spine=pkg.getElementsByTagNameNS("*","itemref");
                for(int i=scope[0]-1;i<Math.min(spine.getLength(),scope[1]);i++){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Search cancelled.");String href=manifest.get(((Element)spine.item(i)).getAttribute("idref"));if(href==null)continue;
                    String resolved=new java.net.URI(null,null,base,null).resolve(href).getPath();org.jsoup.nodes.Document html=Jsoup.parse(new ByteArrayInputStream(entry(zip,resolved,2_000_000)),null,"");html.select("script,style,nav").remove();html.select("p,div,h1,h2,h3,li,br").append("\\n");String text=html.body().wholeText().replace("\\n","\n").replaceAll("\\n{3,}","\n\n").trim();
                    String heading=html.select("h1,h2,title").first()==null?"":html.select("h1,h2,title").first().text();if(!visitor.text("Chapter "+(i+1)+(heading.isEmpty()?"":" · "+heading),text))break;
                }
            }finally{working.delete();}return;
        }
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Document is unavailable. Select it again from Files.");
            BufferedReader reader=new BufferedReader(new InputStreamReader(new FilterInputStream(in){int count;@Override public int read(byte[] b,int o,int l)throws IOException{int n=this.in.read(b,o,l);if(n>0 && (count+=n)>12_000_000)throw new IOException("Text scan limit reached; split the file or use PDF/EPUB page ranges.");return n;}},StandardCharsets.UTF_8));
            StringBuilder block=new StringBuilder();String line;int number=0,unit=1;
            while((line=reader.readLine())!=null){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Search cancelled.");if(line.length()>100000)throw new IOException("This text file has a line longer than the reading limit.");number++;block.append(line).append('\n');if(number%100==0){if(unit>=scope[0] && !visitor.text("Lines "+(number-99)+"-"+number,block.toString()))return;block.setLength(0);if(++unit>scope[1])return;}}
            if(block.length()>0 && unit>=scope[0] && unit<=scope[1])visitor.text("Lines "+(number-number%100+1)+"-"+number,block.toString());
        }
    }
    private static File workingFile(Context context,Uri uri)throws Exception{
        File file=File.createTempFile("book-read-",".tmp",context.getCacheDir());boolean okay=false;
        try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){
            if(in==null)throw new IOException("Document is unavailable. Select it again from Files.");byte[] buffer=new byte[8192];int n;long size=0;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Search cancelled.");if((size+=n)>100_000_000)throw new IOException("This document exceeds the 100 MB working-file limit.");out.write(buffer,0,n);}okay=true;return file;
        }finally{if(!okay)file.delete();}
    }
    private static byte[] entry(ZipFile zip,String path,int max)throws Exception{ZipEntry entry=zip.getEntry(path);if(entry==null)throw new IOException("The EPUB is missing a required section.");try(InputStream in=zip.getInputStream(entry)){return ProviderClient.readBounded(in,max);}}
    private static org.w3c.dom.Document xml(byte[] bytes)throws Exception{
        // Android's XML factory does not support Xerces' disallow-doctype feature.
        // Reject declarations before parsing; resolve no external entity or DTD.
        String source=new String(bytes,StandardCharsets.UTF_8);
        if(source.toUpperCase(Locale.ROOT).contains("<!DOCTYPE") || source.toUpperCase(Locale.ROOT).contains("<!ENTITY") || source.indexOf('\0')>=0)throw new IOException("EPUB package metadata contains unsupported XML declarations.");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder builder=f.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External entities are disabled");});
        return builder.parse(new ByteArrayInputStream(bytes));
    }
}
