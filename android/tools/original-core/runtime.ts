import {toByteArray} from 'base64-js';
import 'fast-text-encoding';
// The upstream graph settle calls D3's stopped simulation and advances it synchronously.
// Plain V8 has no browser timers; prevent a background scheduling lane in this data-only isolate.
const runtime=globalThis as any;
runtime.performance??={now:()=>Date.now()};
runtime.setTimeout??=()=>0;runtime.clearTimeout??=()=>{};
runtime.setInterval??=()=>0;runtime.clearInterval??=()=>{};
// Standard Web atob for HTML entity tables in the bundled article parser.
runtime.atob??=(value:string)=>String.fromCharCode(...toByteArray(value));
