import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {validateScene} from './validation.mjs';

const root=new URL('./',import.meta.url);
const require=createRequire(import.meta.url);
let playwright;
try{playwright=require('playwright');}catch{playwright=require('playwright-core');}
const {chromium}=playwright;
const assets=new Map(await Promise.all(['dist/viewer.js','assets/resources.json','assets/atlas.png'].map(async name=>['/'+name,await readFile(new URL(name,root))])));
const browser=await chromium.launch({headless:true,channel:process.env.SCHEMATIC_CHROME_CHANNEL||undefined,args:['--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage']});
let busy=false;
const html='<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:transparent}#scene{width:1600px;height:640px}</style></head><body><div id="scene"></div><script src="/dist/viewer.js"></script></body></html>';
const server=http.createServer(async(req,res)=>{
  if(req.method==='GET'&&req.url==='/health') {res.setHeader('Content-Type','application/json');res.end(JSON.stringify({status:'UP',busy}));return;}
  if(req.method==='GET'&&req.url==='/viewer') {res.setHeader('Content-Type','text/html');res.end(html);return;}
  if(req.method==='GET'&&assets.has(req.url)) {res.setHeader('Content-Type',req.url.endsWith('.png')?'image/png':req.url.endsWith('.js')?'application/javascript':'application/json');res.end(assets.get(req.url));return;}
  if(req.method!=='POST'||req.url!=='/render') {res.writeHead(404);res.end();return;}
  if(busy) {res.writeHead(429);res.end('渲染器正在处理其他投影');return;}
  busy=true;let context,deadline;
  try {
    const chunks=[];let size=0;
    for await(const chunk of req) {size+=chunk.length;if(size>64*1024*1024)throw new Error('投影数据超过 64 MiB');chunks.push(chunk);}
    const scene=validateScene(JSON.parse(Buffer.concat(chunks).toString('utf8')));
    context=await browser.newContext({viewport:{width:1600,height:640},deviceScaleFactor:1});
    deadline=setTimeout(()=>context.close().catch(()=>{}),90000);
    const page=await context.newPage();const errors=[];
    page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
    const origin=`http://127.0.0.1:${server.address().port}`;
    // The document and every resource are trusted, local assets. Uploaded data never becomes markup or script.
    await page.route('**/*',route=>route.request().url().startsWith(origin+'/')?route.continue():route.abort());
    await page.goto(origin+'/viewer',{waitUntil:'load',timeout:20000});
    await page.evaluate(async()=>SchematicViewer.initialize(await(await fetch('/assets/resources.json')).json(),'/assets/atlas.png'));
    const details=await page.evaluate(scene=>SchematicViewer.render(scene),scene);
    if(errors.length)throw new Error(errors.slice(0,3).join('; '));
    const png=await page.locator('#scene').screenshot({omitBackground:true,timeout:60000});
    res.setHeader('Content-Type','image/png');res.setHeader('X-Schematic-Details',JSON.stringify(details));res.end(png);
  } catch(e) {res.writeHead(400,{'Content-Type':'text/plain;charset=utf-8'});res.end(String(e.message).slice(0,1000));}
  finally {clearTimeout(deadline);await context?.close();busy=false;}
});
server.requestTimeout=120000;
server.listen(Number(process.env.SCHEMATIC_RENDER_PORT||3000),process.env.SCHEMATIC_RENDER_HOST||'0.0.0.0',()=>console.log('Schematic model renderer ready'));
for(const signal of ['SIGTERM','SIGINT'])process.on(signal,async()=>{server.close();await browser.close();process.exit(0);});
