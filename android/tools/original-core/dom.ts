import {DOMParser} from 'linkedom';
// Turndown's browser entry expects a DOMParser; this in-memory parser executes no page scripts.
const environment=globalThis as any;environment.window??={};environment.window.DOMParser??=DOMParser;
