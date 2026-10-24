package ai.learnharness.android;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;
public class OriginalPromptTest {
    @Test public void packagedPromptsExactlyMatchUpstreamComposition()throws Exception{
        Path agent=Paths.get("../../src/agent");
        for(String role:List.of("teacher","prober","practice")){
            List<Path> files=new ArrayList<>();if(!role.equals("practice"))files.add(agent.resolve("system-prompt.md"));
            try(var paths=Files.list(agent.resolve("prompts/"+role))){paths.filter(p->p.getFileName().toString().matches("\\d+-[\\w.-]+\\.md")).sorted().forEach(files::add);}
            try(var paths=Files.list(agent.resolve("prompts/shared"))){paths.filter(p->p.getFileName().toString().matches("\\d+-[\\w.-]+\\.md") && (!role.equals("practice") || List.of("10-voice.md","15-accuracy.md","16-mental-model.md","40-formatting.md").contains(p.getFileName().toString()))).sorted().forEach(files::add);}
            List<String> parts=new ArrayList<>();for(Path file:files){String text=new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.UTF_8).trim();if(!text.isEmpty())parts.add(text);}
            assertEquals(role,String.join("\n\n---\n\n",parts),new String(Files.readAllBytes(Paths.get("build/generated/promptAssets/prompts/"+role+".txt")),java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
