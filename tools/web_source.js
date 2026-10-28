/* Web library data source: the same screens as the APK, fed live from the Brain Library database.
   Injected by tools/make_web.py in place of <!--MZ:SOURCE-->. FALLBACK is replaced with the current content.json. */
window.MZ_SOURCE=(function(){
  "use strict";
  var FALLBACK=/*MZ:FALLBACK*/null;
  var ORDER=["frontend","backend","developer","programmer","security","network","it","automation"];
  var hasClaude=!!(window.claude&&window.claude.use);
  var dbP=hasClaude?window.claude.use("db").catch(function(){return null;}):Promise.resolve(null);
  var sampleP=null, dlP=null;
  function sample(){if(!hasClaude)return Promise.resolve(null);return sampleP||(sampleP=window.claude.use("sample").catch(function(){return null;}));}
  function downloads(){if(!hasClaude)return Promise.resolve(null);return dlP||(dlP=window.claude.use("downloads").catch(function(){return null;}));}

  var brains={}, lessons={}, ready={}, db=null;
  function today(){return new Date().toISOString().slice(0,10);}
  function build(){
    var ids=Object.keys(brains).sort(function(a,b){var x=ORDER.indexOf(a),y=ORDER.indexOf(b);return (x<0?99:x)-(y<0?99:y);});
    var out={rev:0,updated:"",brains:[],lessons:[]};
    ids.forEach(function(id){var b=brains[id]||{};out.brains.push({id:id,name:b.name||id,en:b.en||"",color:b.color||"#5e5ce6",glyph:b.glyph||id.slice(0,1),folders:(b.folders||[]).slice()});
      (lessons[id]||[]).forEach(function(l){out.lessons.push(l);});});
    out.lessons.sort(function(a,b){return (a.date+String(a.ts).padStart(15,"0")).localeCompare(b.date+String(b.ts).padStart(15,"0"));});
    out.updated=out.lessons.length?out.lessons[out.lessons.length-1].date:today();
    return out;
  }
  function toLesson(bid,snap){var d=snap.data()||{};var body=String(d.body||"").trim();if(!body)return null;
    return {id:bid+"-"+snap.id,brain:bid,folder:String(d.folder||"عمومی"),title:String(d.title||"درس"),body:body,source:String(d.source||""),date:String(d.date||""),ts:Number(d.ts)||0};}
  var watching={};
  function watchLessons(bid,onFirst){
    if(watching[bid])return;watching[bid]=1;
    db.collection("brains").doc(bid).collection("lessons").onSnapshot(function(s){
      lessons[bid]=s.docs.map(function(x){return toLesson(bid,x);}).filter(Boolean);
      if(!ready[bid]){ready[bid]=1;onFirst&&onFirst();}else if(window.MZ_onContent)window.MZ_onContent(build());
    },function(){ready[bid]=1;onFirst&&onFirst();});
  }

  function load(){
    return dbP.then(function(d){
      db=d;
      if(!db)return FALLBACK||{rev:0,brains:[],lessons:[]};
      return new Promise(function(resolve){
        var done=false;function finish(){if(done)return;done=true;resolve(build());}
        setTimeout(finish,6000);
        db.collection("brains").onSnapshot(function(s){
          s.docs.forEach(function(x){brains[x.id]=x.data()||{};});
          var ids=Object.keys(brains);
          if(!ids.length){if(FALLBACK){finish();}return;}
          var pending=ids.filter(function(id){return !ready[id];}).length;
          ids.forEach(function(id){watchLessons(id,function(){pending--;if(pending<=0){if(done&&window.MZ_onContent)window.MZ_onContent(build());finish();}});});
          if(done&&window.MZ_onContent)window.MZ_onContent(build());
        },function(){if(FALLBACK){brains={};}finish();});
      }).then(function(c){return c.lessons.length||!FALLBACK?c:FALLBACK;});
    });
  }

  function prompt(b,C){
    var have=C.lessons.filter(function(l){return l.brain===b.id;}).map(function(l){return "• "+l.folder+" › "+l.title;}).slice(-60).join("\n");
    return "تو معلم و مرجع تخصصی مغز «"+b.name+"» ("+b.en+") در کتابخانه دانش امید هستی. یک درس تازه، عمیق و کاربردی برای این مغز بنویس.\n"+
      "پوشه‌های این مغز: "+b.folders.join("، ")+"\n\n"+
      (have?"درس‌هایی که از قبل هست (تکراری نده؛ سراغ پوشه‌های خالی یا کم‌درس برو):\n"+have+"\n\n":"این مغز هنوز خالی است؛ پایه‌ای‌ترین موضوع را انتخاب کن.\n\n")+
      "درس باید مفهوم را روشن توضیح دهد، نمونه‌کد کامل و واقعی در بلوک کد داشته باشد، و بهترین روش‌ها و اشتباه‌های رایج را بگوید. فارسی و دقیق، با تیترهای ## و فهرست‌ها. "+
      (b.id==="security"?"فقط امنیت دفاعی، کدنویسی امن و بررسی سیستم‌های خودِ کاربر؛ هیچ راهنمای حمله یا نفوذ به سیستم دیگران ننویس. ":"")+
      "به اینترنت دسترسی نداری؛ از دانش خودت بنویس و هر جا ممکن است قدیمی شده باشد صادقانه بگو.\n\n"+
      'فقط JSON: {"folder":"<یکی از پوشه‌های بالا>","title":"<عنوان کوتاه>","body":"<متن کامل درس با مارک‌داون>","source":"<مرجع برای مطالعه بیشتر>"}';
  }
  var ERR={rate_limited:"سهمیه Claude پر شده؛ کمی بعد دوباره امتحان کن",refused:"Claude این درس را ننوشت؛ دوباره امتحان کن",invalid_json:"پاسخ قابل خواندن نبود؛ دوباره امتحان کن",not_granted:"اجازه‌ی استفاده از Claude داده نشد",session_expired:"باید دوباره وارد حساب شوی"};
  function generate(bid,C){
    var b=null;C.brains.forEach(function(x){if(x.id===bid)b=x;});if(!b)return Promise.reject("مغز پیدا نشد");
    return sample().then(function(s){
      if(!s)throw {code:"none"};
      return s.json(prompt(b,C),{cache:false,onText:function(u){if(window.MZ_onGenText)window.MZ_onGenText(u.text);}});
    }).then(function(r){
      var folder=b.folders.indexOf(r&&r.folder)>=0?r.folder:(b.folders[0]||"عمومی");
      var doc={brainId:bid,folder:folder,title:String((r&&r.title)||"درس تازه").slice(0,140),body:String((r&&r.body)||""),source:String((r&&r.source)||""),date:today(),isNew:true,ts:Date.now()};
      if(!doc.body.trim())throw {code:"empty_completion"};
      if(!db)throw {code:"nodb"};
      return db.collection("brains").doc(bid).collection("lessons").add(doc).then(function(){return doc.title;});
    }).catch(function(e){throw (e&&ERR[e.code])||(e&&e.code==="nodb"?"این نما به پایگاه داده وصل نیست":(e&&e.code==="none"?"Claude در این نما در دسترس نیست":"درس ساخته نشد؛ دوباره امتحان کن"));});
  }
  function download(bid,C){
    var b=null;C.brains.forEach(function(x){if(x.id===bid)b=x;});
    var ls=C.lessons.filter(function(l){return l.brain===bid;}).sort(function(x,y){return x.folder.localeCompare(y.folder);});
    var md="# مغز "+b.name+" ("+b.en+")\n\nکتابخانه مغزها — "+ls.length+" درس\n",f="";
    ls.forEach(function(l){if(l.folder!==f){f=l.folder;md+="\n\n---\n\n# پوشه: "+f+"\n";}md+="\n## "+l.title+"  ("+l.date+")\n\n"+l.body+(l.source?"\n\n> منبع: "+l.source:"")+"\n";});
    return downloads().then(function(dl){
      if(!dl){try{navigator.clipboard.writeText(md);}catch(e){}return "دانلود در این نما ممکن نیست؛ متن کپی شد";}
      return dl.save({filename:"brain-"+bid+".md",data:md}).then(function(){return "فایل مغز ذخیره شد";},function(e){return e&&e.code==="declined"?"":"دانلود انجام نشد";});
    });
  }
  return {load:load,generate:generate,download:download,canGenerate:hasClaude};
})();
