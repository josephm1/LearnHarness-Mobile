package ai.learnharness.android;
import org.json.*;import org.junit.Test;import java.io.*;import java.nio.charset.StandardCharsets;import static org.junit.Assert.*;
public class ToolProtocolTest {
 private static ByteArrayInputStream stream(JSONObject... events){StringBuilder data=new StringBuilder();for(JSONObject event:events)data.append("data: ").append(event).append("\n\n");return new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.UTF_8));}
 private static JSONObject json(String s)throws Exception{return new JSONObject(s);}
 @Test public void responsesToolOnlyCompletionAndEncryptedReasoningReplay()throws Exception{
  JSONArray output=new JSONArray().put(json("{type:'reasoning',encrypted_content:'opaque'}")).put(json("{type:'function_call',call_id:'call_1',name:'probe',arguments:'{}'}"));
  var reply=ResponseStream.read(stream(new JSONObject().put("type","response.completed").put("response",new JSONObject().put("status","completed").put("output",output))));assertEquals("",reply.text());assertEquals(1,reply.toolCalls().length());
  JSONArray history=new JSONArray().put(ToolProtocol.assistant(reply)).put(ToolProtocol.output(reply.toolCalls().getJSONObject(0),json("{correct:true}")));JSONArray input=ToolProtocol.messages(history,"Responses");assertEquals("opaque",input.getJSONObject(0).getString("encrypted_content"));assertEquals("call_1",input.getJSONObject(2).getString("call_id"));assertFalse(input.toString().contains("_native"));
 }
 @Test public void chatToolArgumentsAreAssembledAcrossDeltasWithoutLeakingToPreview()throws Exception{
  JSONObject a=json("{choices:[{delta:{tool_calls:[{index:0,id:'call',function:{name:'probe',arguments:'{'}}]}}]}");
  JSONObject b=json("{choices:[{delta:{tool_calls:[{index:0,function:{arguments:'}'}}]},finish_reason:'tool_calls'}]}");
  var reply=ProviderStream.read(stream(a,b),"OpenRouter",text->fail("Tool payload leaked"));assertEquals("{}",reply.toolCalls().getJSONObject(0).getJSONObject("function").getString("arguments"));
 }
 @Test public void anthropicToolJsonAndThinkingSignatureSurviveReplay()throws Exception{
  var reply=ProviderStream.read(stream(json("{type:'content_block_start',index:0,content_block:{type:'thinking',thinking:'',signature:''}}"),json("{type:'content_block_delta',index:0,delta:{type:'thinking_delta',thinking:'private'}}"),json("{type:'content_block_delta',index:0,delta:{type:'signature_delta',signature:'opaque'}}"),json("{type:'content_block_start',index:1,content_block:{type:'tool_use',id:'tool1',name:'recall',input:{}}}"),json("{type:'content_block_delta',index:1,delta:{type:'input_json_delta',partial_json:'{}'}}"),json("{type:'content_block_stop',index:1}"),json("{type:'message_delta',delta:{stop_reason:'tool_use'}}"),json("{type:'message_stop'}")),"Anthropic",t->fail());
  assertEquals(1,reply.toolCalls().length());JSONArray history=new JSONArray().put(ToolProtocol.assistant(reply)).put(ToolProtocol.output(reply.toolCalls().getJSONObject(0),json("{text:'my answer'}")));JSONArray messages=ToolProtocol.messages(history,"Anthropic");assertEquals("opaque",messages.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("signature"));assertEquals("tool1",messages.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("tool_use_id"));assertEquals("",reply.text());
 }
 @Test public void geminiFunctionPartSignatureSurvivesReplay()throws Exception{
  var reply=ProviderStream.read(stream(json("{candidates:[{content:{parts:[{functionCall:{id:'gemini_native_id',name:'probe',args:{question:'Q'}},thoughtSignature:'opaque'}]},finishReason:'STOP'}]}")),"Gemini",t->fail());JSONArray messages=ToolProtocol.messages(new JSONArray().put(ToolProtocol.assistant(reply)).put(ToolProtocol.output(reply.toolCalls().getJSONObject(0),json("{correct:true}"))),"Gemini");assertEquals("opaque",messages.getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("thoughtSignature"));assertEquals("gemini_native_id",messages.getJSONObject(1).getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse").getString("id"));assertEquals("probe",messages.getJSONObject(1).getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse").getString("name"));
 }
 @Test public void approvedSourceIsRemovedBeforeToolHistoryIsSaved()throws Exception{
  JSONArray history=new JSONArray().put(json("{role:'tool',tool_call_id:'q',name:'recall',content:'my answer'}"));JSONArray request=SourceInput.forTurn(history,"private book passage");assertTrue(ToolProtocol.messages(request,"Chat").toString().contains("private book passage"));assertFalse(ToolProtocol.messages(request,"Chat").toString().contains("_withoutApprovedSource"));assertEquals(history.toString(),SourceInput.forStorage(request).toString());
 }
 @Test public void metadataIsNotSentToCompatibleProviders()throws Exception{JSONArray history=new JSONArray().put(json("{role:'assistant',content:'hello',_native:{responses:[{type:'reasoning',encrypted_content:'opaque'}]}}"));assertEquals("[{\"role\":\"assistant\",\"content\":\"hello\"}]",ToolProtocol.messages(history,"Chat").toString());}

 @Test public void routerSummaryFragmentsAreMergedWhileOpaqueBlocksStaySeparate()throws Exception{
  var reply=ProviderStream.read(stream(json("{choices:[{delta:{reasoning_details:[{type:'reasoning.summary',summary:'First ',index:0}]}}]}"),json("{choices:[{delta:{reasoning_details:[{type:'reasoning.summary',summary:'then second',index:0}]}}]}"),json("{choices:[{delta:{reasoning_details:[{type:'reasoning.encrypted',data:'opaque',id:'r',index:0}],content:'Answer'},finish_reason:'stop'}]}")),"OpenRouter",t->{});
  JSONArray details=reply.nativeOutput().getJSONArray("reasoning_details");assertEquals(2,details.length());assertEquals("First then second",details.getJSONObject(0).getString("summary"));assertEquals("opaque",details.getJSONObject(1).getString("data"));assertEquals("Answer",reply.text());
 }
 @Test public void geminiReceivesOriginalJsonSchemaWithoutLossyOpenApiConversion()throws Exception{JSONArray declarations=ToolProtocol.declarations(new JSONArray().put(json("{name:'quiz',description:'Original',parameters:{type:'object',properties:{type:{anyOf:[{const:'check'},{const:'predict'}]}}}}")),"Gemini");assertTrue(declarations.getJSONObject(0).has("parametersJsonSchema"));assertFalse(declarations.getJSONObject(0).has("parameters"));}
}
