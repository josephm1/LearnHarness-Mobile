package ai.learnharness.android;
/** Presentation-only bridge from upstream $inline$ syntax to Markwon's $$inline$$ syntax. */
public final class MarkdownMath {
 private MarkdownMath(){}
 public static String normalize(String text){
  StringBuilder result=new StringBuilder();int code=0;boolean display=false;
  for(int i=0;i<text.length();){char c=text.charAt(i);
   if(c=='`' || c=='~' && (i==0 || text.charAt(i-1)=='\n')){int end=i+1;while(end<text.length() && text.charAt(end)==c)end++;int run=end-i;if(c=='`' || run>=3){if(code==0)code=c=='~'?-run:run;else if(code==(c=='~'?-run:run))code=0;result.append(text,i,end);i=end;continue;}}
   if(c=='$' && code==0 && !escaped(text,i)){
    if(i+1<text.length() && text.charAt(i+1)=='$'){result.append("$$");display=!display;i+=2;continue;}
    if(!display){int end=i+1;while(end<text.length() && text.charAt(end)!='\n'){if(text.charAt(end)=='$' && !escaped(text,end))break;end++;}if(end<text.length() && text.charAt(end)=='$' && end>i+1 && (end+1==text.length() || text.charAt(end+1)!='$')){result.append("$$").append(text,i+1,end).append("$$");i=end+1;continue;}}
   }
   result.append(c);i++;
  }
  return result.toString();
 }
 private static boolean escaped(String text,int index){int slashes=0;for(int i=index-1;i>=0 && text.charAt(i)=='\\';i--)slashes++;return slashes%2==1;}
}
