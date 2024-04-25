/**
 * State-based routing for AngularJS
 * @version v0.2.11
 * @link http://angular-ui.github.com/
 * @license MIT License, http://www.opensource.org/licenses/MIT
 */
"undefined"!=typeof module&&"undefined"!=typeof exports&&module.exports===exports&&(module.exports="ui.router"),function(window,angular,undefined){"use strict";var isDefined=angular.isDefined,isFunction=angular.isFunction,isString=angular.isString,isObject=angular.isObject,isArray=angular.isArray,forEach=angular.forEach,extend=angular.extend,copy=angular.copy;function inherit(parent,extra){return extend(new(extend((function(){}),{prototype:parent})),extra)}function merge(dst){return forEach(arguments,(function(obj){obj!==dst&&forEach(obj,(function(value,key){dst.hasOwnProperty(key)||(dst[key]=value)}))})),dst}function objectKeys(object){if(Object.keys)return Object.keys(object);var result=[];return angular.forEach(object,(function(val,key){result.push(key)})),result}function arraySearch(array,value){if(Array.prototype.indexOf)return array.indexOf(value,Number(arguments[2])||0);var len=array.length>>>0,from=Number(arguments[2])||0;for((from=from<0?Math.ceil(from):Math.floor(from))<0&&(from+=len);from<len;from++)if(from in array&&array[from]===value)return from;return-1}function inheritParams(currentParams,newParams,$current,$to){var parentParams,parents=function(first,second){var path=[];for(var n in first.path){if(first.path[n]!==second.path[n])break;path.push(first.path[n])}return path}($current,$to),inherited={},inheritList=[];for(var i in parents)if(parents[i].params&&(parentParams=objectKeys(parents[i].params)).length)for(var j in parentParams)arraySearch(inheritList,parentParams[j])>=0||(inheritList.push(parentParams[j]),inherited[parentParams[j]]=currentParams[parentParams[j]]);return extend({},inherited,newParams)}function equalForKeys(a,b,keys){if(!keys)for(var n in keys=[],a)keys.push(n);for(var i=0;i<keys.length;i++){var k=keys[i];if(a[k]!=b[k])return!1}return!0}function filterByKeys(keys,values){var filtered={};return forEach(keys,(function(name){filtered[name]=values[name]})),filtered}function $Resolve($q,$injector){var NOTHING={},NO_DEPENDENCIES=[],NO_LOCALS=NOTHING,NO_PARENT=extend($q.when(NOTHING),{$$promises:NOTHING,$$values:NOTHING});this.study=function(invocables){if(!isObject(invocables))throw new Error("'invocables' must be an object");var plan=[],cycle=[],visited={};function isResolve(value){return isObject(value)&&value.then&&value.$$promises}return forEach(invocables,(function visit(value,key){if(2!==visited[key]){if(cycle.push(key),1===visited[key])throw cycle.splice(0,cycle.indexOf(key)),new Error("Cyclic dependency: "+cycle.join(" -> "));if(visited[key]=1,isString(value))plan.push(key,[function(){return $injector.get(value)}],NO_DEPENDENCIES);else{var params=$injector.annotate(value);forEach(params,(function(param){param!==key&&invocables.hasOwnProperty(param)&&visit(invocables[param],param)})),plan.push(key,value,params)}cycle.pop(),visited[key]=2}})),invocables=cycle=visited=null,function(locals,parent,self){if(isResolve(locals)&&void 0===self&&(self=parent,parent=locals,locals=null),locals){if(!isObject(locals))throw new Error("'locals' must be an object")}else locals=NO_LOCALS;if(parent){if(!isResolve(parent))throw new Error("'parent' must be a promise returned by $resolve.resolve()")}else parent=NO_PARENT;var resolution=$q.defer(),result=resolution.promise,promises=result.$$promises={},values=extend({},locals),wait=1+plan.length/3,merged=!1;function done(){--wait||(merged||merge(values,parent.$$values),result.$$values=values,result.$$promises=!0,delete result.$$inheritedValues,resolution.resolve(values))}function fail(reason){result.$$failure=reason,resolution.reject(reason)}if(isDefined(parent.$$failure))return fail(parent.$$failure),result;parent.$$inheritedValues&&merge(values,parent.$$inheritedValues),parent.$$values?(merged=merge(values,parent.$$values),result.$$inheritedValues=parent.$$values,done()):(parent.$$inheritedValues&&(result.$$inheritedValues=parent.$$inheritedValues),extend(promises,parent.$$promises),parent.then(done,fail));for(var i=0,ii=plan.length;i<ii;i+=3)locals.hasOwnProperty(plan[i])?done():invoke(plan[i],plan[i+1],plan[i+2]);function invoke(key,invocable,params){var invocation=$q.defer(),waitParams=0;function onfailure(reason){invocation.reject(reason),fail(reason)}function proceed(){if(!isDefined(result.$$failure))try{invocation.resolve($injector.invoke(invocable,self,values)),invocation.promise.then((function(result){values[key]=result,done()}),onfailure)}catch(e){onfailure(e)}}forEach(params,(function(dep){promises.hasOwnProperty(dep)&&!locals.hasOwnProperty(dep)&&(waitParams++,promises[dep].then((function(result){values[dep]=result,--waitParams||proceed()}),onfailure))})),waitParams||proceed(),promises[key]=invocation.promise}return result}},this.resolve=function(invocables,locals,parent,self){return this.study(invocables)(locals,parent,self)}}function $TemplateFactory($http,$templateCache,$injector){this.fromConfig=function(config,params,locals){return isDefined(config.template)?this.fromString(config.template,params):isDefined(config.templateUrl)?this.fromUrl(config.templateUrl,params):isDefined(config.templateProvider)?this.fromProvider(config.templateProvider,params,locals):null},this.fromString=function(template,params){return isFunction(template)?template(params):template},this.fromUrl=function(url,params){return isFunction(url)&&(url=url(params)),null==url?null:$http.get(url,{cache:$templateCache}).then((function(response){return response.data}))},this.fromProvider=function(provider,params,locals){return $injector.invoke(provider,null,locals||{params:params})}}function UrlMatcher(pattern,config){config=angular.isObject(config)?config:{};var m,id,regexp,segment,type,cfg,placeholder=/([:*])(\w+)|\{(\w+)(?:\:((?:[^{}\\]+|\\.|\{(?:[^{}\\]+|\\.)*\})+))?\}/g,compiled="^",last=0,segments=this.segments=[],params=this.params={};function $value(value){return isDefined(value)?this.type.decode(value):$UrlMatcherFactory.$$getDefaultValue(this)}function addParameter(id,type,config){if(!/^\w+(-+\w+)*$/.test(id))throw new Error("Invalid parameter name '"+id+"' in pattern '"+pattern+"'");if(params[id])throw new Error("Duplicate parameter name '"+id+"' in pattern '"+pattern+"'");params[id]=extend({type:type||new Type,$value:$value},config)}function quoteRegExp(string,pattern,isOptional){var result=string.replace(/[\\\[\]\^$*+?.()|{}]/g,"\\$&");if(!pattern)return result;var flag=isOptional?"?":"";return result+flag+"("+pattern+")"+flag}function paramConfig(param){if(!config.params||!config.params[param])return{};var cfg=config.params[param];return isObject(cfg)?cfg:{value:cfg}}for(this.source=pattern;(m=placeholder.exec(pattern))&&(id=m[2]||m[3],regexp=m[4]||("*"==m[1]?".*":"[^/]*"),segment=pattern.substring(last,m.index),type=this.$types[regexp]||new Type({pattern:new RegExp(regexp)}),cfg=paramConfig(id),!(segment.indexOf("?")>=0));)compiled+=quoteRegExp(segment,type.$subPattern(),isDefined(cfg.value)),addParameter(id,type,cfg),segments.push(segment),last=placeholder.lastIndex;var i=(segment=pattern.substring(last)).indexOf("?");if(i>=0){var search=this.sourceSearch=segment.substring(i);segment=segment.substring(0,i),this.sourcePath=pattern.substring(0,last+i),forEach(search.substring(1).split(/[&?]/),(function(key){addParameter(key,null,paramConfig(key))}))}else this.sourcePath=pattern,this.sourceSearch="";compiled+=quoteRegExp(segment)+(!1===config.strict?"/?":"")+"$",segments.push(segment),this.regexp=new RegExp(compiled,config.caseInsensitive?"i":void 0),this.prefix=segments[0]}function Type(config){extend(this,config)}function $UrlMatcherFactory(){var injector,isCaseInsensitive=!1,isStrictMode=!0,enqueue=!0,typeQueue=[],defaultTypes={int:{decode:function(val){return parseInt(val,10)},is:function(val){return!!isDefined(val)&&this.decode(val.toString())===val},pattern:/\d+/},bool:{encode:function(val){return val?1:0},decode:function(val){return 0!==parseInt(val,10)},is:function(val){return!0===val||!1===val},pattern:/0|1/},string:{pattern:/[^\/]*/},date:{equals:function(a,b){return a.toISOString()===b.toISOString()},decode:function(val){return new Date(val)},encode:function(val){return[val.getFullYear(),("0"+(val.getMonth()+1)).slice(-2),("0"+val.getDate()).slice(-2)].join("-")},pattern:/[0-9]{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[1-2][0-9]|3[0-1])/}};function isInjectable(value){return isFunction(value)||isArray(value)&&isFunction(value[value.length-1])}function flushTypeQueue(){forEach(typeQueue,(function(type){if(UrlMatcher.prototype.$types[type.name])throw new Error("A type named '"+type.name+"' has already been defined.");var def=new Type(isInjectable(type.def)?injector.invoke(type.def):type.def);UrlMatcher.prototype.$types[type.name]=def}))}$UrlMatcherFactory.$$getDefaultValue=function(config){if(!isInjectable(config.value))return config.value;if(!injector)throw new Error("Injectable functions cannot be called at configuration time");return injector.invoke(config.value)},this.caseInsensitive=function(value){isCaseInsensitive=value},this.strictMode=function(value){isStrictMode=value},this.compile=function(pattern,config){return new UrlMatcher(pattern,extend({strict:isStrictMode,caseInsensitive:isCaseInsensitive},config))},this.isMatcher=function(o){if(!isObject(o))return!1;var result=!0;return forEach(UrlMatcher.prototype,(function(val,name){isFunction(val)&&(result=result&&isDefined(o[name])&&isFunction(o[name]))})),result},this.type=function(name,def){return isDefined(def)?(typeQueue.push({name:name,def:def}),enqueue||flushTypeQueue(),this):UrlMatcher.prototype.$types[name]},this.$get=["$injector",function($injector){return injector=$injector,enqueue=!1,UrlMatcher.prototype.$types={},flushTypeQueue(),forEach(defaultTypes,(function(type,name){UrlMatcher.prototype.$types[name]||(UrlMatcher.prototype.$types[name]=new Type(type))})),this}]}function $UrlRouterProvider($locationProvider,$urlMatcherFactory){var listener,rules=[],otherwise=null,interceptDeferred=!1;function handleIfMatch($injector,handler,match){if(!match)return!1;var result=$injector.invoke(handler,handler,{$match:match});return!isDefined(result)||result}function $get($location,$rootScope,$injector,$browser){var baseHref=$browser.baseHref(),location=$location.url();function update(evt){if(!evt||!evt.defaultPrevented){var i,n=rules.length;for(i=0;i<n;i++)if(check(rules[i]))return;otherwise&&check(otherwise)}function check(rule){var handled=rule($injector,$location);return!!handled&&(isString(handled)&&$location.replace().url(handled),!0)}}function listen(){return listener=listener||$rootScope.$on("$locationChangeSuccess",update)}return interceptDeferred||listen(),{sync:function(){update()},listen:function(){return listen()},update:function(read){read?location=$location.url():$location.url()!==location&&($location.url(location),$location.replace())},push:function(urlMatcher,params,options){$location.url(urlMatcher.format(params||{})),options&&options.replace&&$location.replace()},href:function(urlMatcher,params,options){if(!urlMatcher.validates(params))return null;var isHtml5=$locationProvider.html5Mode(),url=urlMatcher.format(params);if(options=options||{},isHtml5||null===url||(url="#"+$locationProvider.hashPrefix()+url),url=function(url,isHtml5,absolute){return"/"===baseHref?url:isHtml5?baseHref.slice(0,-1)+url:absolute?baseHref.slice(1)+url:url}(url,isHtml5,options.absolute),!options.absolute||!url)return url;var slash=!isHtml5&&url?"/":"",port=$location.port();return port=80===port||443===port?"":":"+port,[$location.protocol(),"://",$location.host(),port,slash,url].join("")}}}this.rule=function(rule){if(!isFunction(rule))throw new Error("'rule' must be a function");return rules.push(rule),this},this.otherwise=function(rule){if(isString(rule)){var redirect=rule;rule=function(){return redirect}}else if(!isFunction(rule))throw new Error("'rule' must be a function");return otherwise=rule,this},this.when=function(what,handler){var redirect,handlerIsString=isString(handler);if(isString(what)&&(what=$urlMatcherFactory.compile(what)),!handlerIsString&&!isFunction(handler)&&!isArray(handler))throw new Error("invalid 'handler' in when()");var strategies={matcher:function(what,handler){return handlerIsString&&(redirect=$urlMatcherFactory.compile(handler),handler=["$match",function($match){return redirect.format($match)}]),extend((function($injector,$location){return handleIfMatch($injector,handler,what.exec($location.path(),$location.search()))}),{prefix:isString(what.prefix)?what.prefix:""})},regex:function(what,handler){if(what.global||what.sticky)throw new Error("when() RegExp must not be global or sticky");return handlerIsString&&(redirect=handler,handler=["$match",function($match){return match=$match,redirect.replace(/\$(\$|\d{1,2})/,(function(m,what){return match["$"===what?0:Number(what)]}));var match}]),extend((function($injector,$location){return handleIfMatch($injector,handler,what.exec($location.path()))}),{prefix:(re=what,prefix=/^\^((?:\\[^a-zA-Z0-9]|[^\\\[\]\^$*+?.()|{}]+)*)/.exec(re.source),null!=prefix?prefix[1].replace(/\\(.)/g,"$1"):"")});var re,prefix}},check={matcher:$urlMatcherFactory.isMatcher(what),regex:what instanceof RegExp};for(var n in check)if(check[n])return this.rule(strategies[n](what,handler));throw new Error("invalid 'what' in when()")},this.deferIntercept=function(defer){void 0===defer&&(defer=!0),interceptDeferred=defer},this.$get=$get,$get.$inject=["$location","$rootScope","$injector","$browser"]}function $StateProvider($urlRouterProvider,$urlMatcherFactory){var root,$state,states={},queue={},stateBuilder={parent:function(state){if(isDefined(state.parent)&&state.parent)return findState(state.parent);var compositeName=/^(.+)\.[^.]+$/.exec(state.name);return compositeName?findState(compositeName[1]):root},data:function(state){return state.parent&&state.parent.data&&(state.data=state.self.data=extend({},state.parent.data,state.data)),state.data},url:function(state){var url=state.url,config={params:state.params||{}};if(isString(url))return"^"==url.charAt(0)?$urlMatcherFactory.compile(url.substring(1),config):(state.parent.navigable||root).url.concat(url,config);if(!url||$urlMatcherFactory.isMatcher(url))return url;throw new Error("Invalid url '"+url+"' in state '"+state+"'")},navigable:function(state){return state.url?state:state.parent?state.parent.navigable:null},params:function(state){return state.params?state.params:state.url?state.url.params:state.parent.params},views:function(state){var views={};return forEach(isDefined(state.views)?state.views:{"":state},(function(view,name){name.indexOf("@")<0&&(name+="@"+state.parent.name),views[name]=view})),views},ownParams:function(state){if(state.params=state.params||{},!state.parent)return objectKeys(state.params);var paramNames={};forEach(state.params,(function(v,k){paramNames[k]=!0})),forEach(state.parent.params,(function(v,k){if(!paramNames[k])throw new Error("Missing required parameter '"+k+"' in state '"+state.name+"'");paramNames[k]=!1}));var ownParams=[];return forEach(paramNames,(function(own,p){own&&ownParams.push(p)})),ownParams},path:function(state){return state.parent?state.parent.path.concat(state):[]},includes:function(state){var includes=state.parent?extend({},state.parent.includes):{};return includes[state.name]=!0,includes},$delegates:{}};function findState(stateOrName,base){if(stateOrName){var stateName,isStr=isString(stateOrName),name=isStr?stateOrName:stateOrName.name;if(0===(stateName=name).indexOf(".")||0===stateName.indexOf("^")){if(!base)throw new Error("No reference point given for path '"+name+"'");for(var rel=name.split("."),i=0,pathLength=rel.length,current=base;i<pathLength;i++)if(""!==rel[i]||0!==i){if("^"!==rel[i])break;if(!current.parent)throw new Error("Path '"+name+"' not valid for state '"+base.name+"'");current=current.parent}else current=base;rel=rel.slice(i).join("."),name=current.name+(current.name&&rel?".":"")+rel}var state=states[name];return!state||!isStr&&(isStr||state!==stateOrName&&state.self!==stateOrName)?void 0:state}}function registerState(state){var name=(state=inherit(state,{self:state,resolve:state.resolve||{},toString:function(){return this.name}})).name;if(!isString(name)||name.indexOf("@")>=0)throw new Error("State must have a valid name");if(states.hasOwnProperty(name))throw new Error("State '"+name+"'' is already defined");var parentName=-1!==name.indexOf(".")?name.substring(0,name.lastIndexOf(".")):isString(state.parent)?state.parent:"";if(parentName&&!states[parentName])return function(parentName,state){queue[parentName]||(queue[parentName]=[]),queue[parentName].push(state)}(parentName,state.self);for(var key in stateBuilder)isFunction(stateBuilder[key])&&(state[key]=stateBuilder[key](state,stateBuilder.$delegates[key]));if(states[name]=state,!state.abstract&&state.url&&$urlRouterProvider.when(state.url,["$match","$stateParams",function($match,$stateParams){$state.$current.navigable==state&&equalForKeys($match,$stateParams)||$state.transitionTo(state,$match,{location:!1})}]),queue[name])for(var i=0;i<queue[name].length;i++)registerState(queue[name][i]);return state}function $get($rootScope,$q,$view,$injector,$resolve,$stateParams,$urlRouter){var TransitionSuperseded=$q.reject(new Error("transition superseded")),TransitionPrevented=$q.reject(new Error("transition prevented")),TransitionAborted=$q.reject(new Error("transition aborted")),TransitionFailed=$q.reject(new Error("transition failed"));function resolveState(state,params,paramsAreFiltered,inherited,dst){var $stateParams=paramsAreFiltered?params:filterByKeys(objectKeys(state.params),params),locals={$stateParams:$stateParams};dst.resolve=$resolve.resolve(state.resolve,locals,dst.resolve,state);var promises=[dst.resolve.then((function(globals){dst.globals=globals}))];return inherited&&promises.push(inherited),forEach(state.views,(function(view,name){var injectables=view.resolve&&view.resolve!==state.resolve?view.resolve:{};injectables.$template=[function(){return $view.load(name,{view:view,locals:locals,params:$stateParams})||""}],promises.push($resolve.resolve(injectables,locals,dst.resolve,state).then((function(result){if(isFunction(view.controllerProvider)||isArray(view.controllerProvider)){var injectLocals=angular.extend({},injectables,locals);result.$$controller=$injector.invoke(view.controllerProvider,null,injectLocals)}else result.$$controller=view.controller;result.$$state=state,result.$$controllerAs=view.controllerAs,dst[name]=result})))})),$q.all(promises).then((function(values){return dst}))}return root.locals={resolve:null,globals:{$stateParams:{}}},($state={params:{},current:root.self,$current:root,transition:null}).reload=function(){$state.transitionTo($state.current,$stateParams,{reload:!0,inherit:!1,notify:!1})},$state.go=function(to,params,options){return $state.transitionTo(to,params,extend({inherit:!0,relative:$state.$current},options))},$state.transitionTo=function(to,toParams,options){toParams=toParams||{},options=extend({location:!0,inherit:!1,relative:null,notify:!0,reload:!1,$retry:!1},options||{});var from=$state.$current,fromParams=$state.params,fromPath=from.path,toState=findState(to,options.relative);if(!isDefined(toState)){var redirect={to:to,toParams:toParams,options:options},redirectResult=function(redirect,state,params,options){var evt=$rootScope.$broadcast("$stateNotFound",redirect,state,params);if(evt.defaultPrevented)return $urlRouter.update(),TransitionAborted;if(!evt.retry)return null;if(options.$retry)return $urlRouter.update(),TransitionFailed;var retryTransition=$state.transition=$q.when(evt.retry);return retryTransition.then((function(){return retryTransition!==$state.transition?TransitionSuperseded:(redirect.options.$retry=!0,$state.transitionTo(redirect.to,redirect.toParams,redirect.options))}),(function(){return TransitionAborted})),$urlRouter.update(),retryTransition}(redirect,from.self,fromParams,options);if(redirectResult)return redirectResult;if(toParams=redirect.toParams,toState=findState(to=redirect.to,(options=redirect.options).relative),!isDefined(toState)){if(!options.relative)throw new Error("No such state '"+to+"'");throw new Error("Could not resolve '"+to+"' from state '"+options.relative+"'")}}if(toState.abstract)throw new Error("Cannot transition to abstract state '"+to+"'");options.inherit&&(toParams=inheritParams($stateParams,toParams||{},$state.$current,toState));var toPath=(to=toState).path,keep=0,state=toPath[keep],locals=root.locals,toLocals=[];if(!options.reload)for(;state&&state===fromPath[keep]&&equalForKeys(toParams,fromParams,state.ownParams);)locals=toLocals[keep]=state.locals,keep++,state=toPath[keep];if(function(to,from,locals,options){if(to===from&&(locals===from.locals&&!options.reload||!1===to.self.reloadOnSearch))return!0}(to,from,locals,options))return!1!==to.self.reloadOnSearch&&$urlRouter.update(),$state.transition=null,$q.when($state.current);if(toParams=filterByKeys(objectKeys(to.params),toParams||{}),options.notify&&$rootScope.$broadcast("$stateChangeStart",to.self,toParams,from.self,fromParams).defaultPrevented)return $urlRouter.update(),TransitionPrevented;for(var resolved=$q.when(locals),l=keep;l<toPath.length;l++,state=toPath[l])locals=toLocals[l]=inherit(locals),resolved=resolveState(state,toParams,state===to,resolved,locals);var transition=$state.transition=resolved.then((function(){var l,entering,exiting;if($state.transition!==transition)return TransitionSuperseded;for(l=fromPath.length-1;l>=keep;l--)(exiting=fromPath[l]).self.onExit&&$injector.invoke(exiting.self.onExit,exiting.self,exiting.locals.globals),exiting.locals=null;for(l=keep;l<toPath.length;l++)(entering=toPath[l]).locals=toLocals[l],entering.self.onEnter&&$injector.invoke(entering.self.onEnter,entering.self,entering.locals.globals);return $state.transition!==transition?TransitionSuperseded:($state.$current=to,$state.current=to.self,$state.params=toParams,copy($state.params,$stateParams),$state.transition=null,options.location&&to.navigable&&$urlRouter.push(to.navigable.url,to.navigable.locals.globals.$stateParams,{replace:"replace"===options.location}),options.notify&&$rootScope.$broadcast("$stateChangeSuccess",to.self,toParams,from.self,fromParams),$urlRouter.update(!0),$state.current)}),(function(error){return $state.transition!==transition?TransitionSuperseded:($state.transition=null,$rootScope.$broadcast("$stateChangeError",to.self,toParams,from.self,fromParams,error).defaultPrevented||$urlRouter.update(),$q.reject(error))}));return transition},$state.is=function(stateOrName,params){var state=findState(stateOrName);if(isDefined(state))return $state.$current===state&&(!isDefined(params)||null===params||angular.equals($stateParams,params))},$state.includes=function(stateOrName,params){if(isString(stateOrName)&&stateOrName.indexOf("*")>-1){if(!function(glob){var globSegments=glob.split("."),segments=$state.$current.name.split(".");if("**"===globSegments[0]&&(segments=segments.slice(segments.indexOf(globSegments[1]))).unshift("**"),"**"===globSegments[globSegments.length-1]&&(segments.splice(segments.indexOf(globSegments[globSegments.length-2])+1,Number.MAX_VALUE),segments.push("**")),globSegments.length!=segments.length)return!1;for(var i=0,l=globSegments.length;i<l;i++)"*"===globSegments[i]&&(segments[i]="*");return segments.join("")===globSegments.join("")}(stateOrName))return!1;stateOrName=$state.$current.name}var state=findState(stateOrName);if(isDefined(state))return!!isDefined($state.$current.includes[state.name])&&equalForKeys(params,$stateParams)},$state.href=function(stateOrName,params,options){var state=findState(stateOrName,(options=extend({lossy:!0,inherit:!0,absolute:!1,relative:$state.$current},options||{})).relative);if(!isDefined(state))return null;options.inherit&&(params=inheritParams($stateParams,params||{},$state.$current,state));var nav=state&&options.lossy?state.navigable:state;return nav&&nav.url?$urlRouter.href(nav.url,filterByKeys(objectKeys(state.params),params||{}),{absolute:options.absolute}):null},$state.get=function(stateOrName,context){if(0===arguments.length)return objectKeys(states).map((function(name){return states[name].self}));var state=findState(stateOrName,context);return state&&state.self?state.self:null},$state}(root=registerState({name:"",url:"^",views:null,abstract:!0})).navigable=null,this.decorator=function(name,func){if(isString(name)&&!isDefined(func))return stateBuilder[name];if(!isFunction(func)||!isString(name))return this;stateBuilder[name]&&!stateBuilder.$delegates[name]&&(stateBuilder.$delegates[name]=stateBuilder[name]);return stateBuilder[name]=func,this},this.state=function(name,definition){isObject(name)?definition=name:definition.name=name;return registerState(definition),this},this.$get=$get,$get.$inject=["$rootScope","$q","$view","$injector","$resolve","$stateParams","$urlRouter"]}function $ViewProvider(){function $get($rootScope,$templateFactory){return{load:function(name,options){var result;return(options=extend({template:null,controller:null,view:null,locals:null,notify:!0,async:!0,params:{}},options)).view&&(result=$templateFactory.fromConfig(options.view,options.params,options.locals)),result&&options.notify&&$rootScope.$broadcast("$viewContentLoading",options),result}}}this.$get=$get,$get.$inject=["$rootScope","$templateFactory"]}function $ViewDirective($state,$injector,$uiViewScroll){var service=$injector.has?function(service){return $injector.has(service)?$injector.get(service):null}:function(service){try{return $injector.get(service)}catch(e){return null}},$animator=service("$animator"),$animate=service("$animate");return{restrict:"ECA",terminal:!0,priority:400,transclude:"element",compile:function(tElement,tAttrs,$transclude){return function(scope,$element,attrs){var previousEl,currentEl,currentScope,latestLocals,onloadExp=attrs.onload||"",autoScrollExp=attrs.autoscroll,renderer=function(attrs,scope){if($animate)return{enter:function(element,target,cb){$animate.enter(element,null,target,cb)},leave:function(element,cb){$animate.leave(element,cb)}};if($animator){var animate=$animator&&$animator(scope,attrs);return{enter:function(element,target,cb){animate.enter(element,null,target),cb()},leave:function(element,cb){animate.leave(element),cb()}}}return{enter:function(element,target,cb){target.after(element),cb()},leave:function(element,cb){element.remove(),cb()}}}(attrs,scope);function updateView(firstTime){var newScope,name=getUiViewName(attrs,$element.inheritedData("$uiView")),previousLocals=name&&$state.$current&&$state.$current.locals[name];if(firstTime||previousLocals!==latestLocals){newScope=scope.$new(),latestLocals=$state.$current.locals[name];var clone=$transclude(newScope,(function(clone){renderer.enter(clone,$element,(function(){(angular.isDefined(autoScrollExp)&&!autoScrollExp||scope.$eval(autoScrollExp))&&$uiViewScroll(clone)})),previousEl&&(previousEl.remove(),previousEl=null),currentScope&&(currentScope.$destroy(),currentScope=null),currentEl&&(renderer.leave(currentEl,(function(){previousEl=null})),previousEl=currentEl,currentEl=null)}));currentEl=clone,(currentScope=newScope).$emit("$viewContentLoaded"),currentScope.$eval(onloadExp)}}scope.$on("$stateChangeSuccess",(function(){updateView(!1)})),scope.$on("$viewContentLoading",(function(){updateView(!1)})),updateView(!0)}}}}function $ViewDirectiveFill($compile,$controller,$state){return{restrict:"ECA",priority:-400,compile:function(tElement){var initial=tElement.html();return function(scope,$element,attrs){var current=$state.$current,name=getUiViewName(attrs,$element.inheritedData("$uiView")),locals=current&&current.locals[name];if(locals){$element.data("$uiView",{name:name,state:locals.$$state}),$element.html(locals.$template?locals.$template:initial);var link=$compile($element.contents());if(locals.$$controller){locals.$scope=scope;var controller=$controller(locals.$$controller,locals);locals.$$controllerAs&&(scope[locals.$$controllerAs]=controller),$element.data("$ngControllerController",controller),$element.children().data("$ngControllerController",controller)}link(scope)}}}}}function getUiViewName(attrs,inherited){var name=attrs.uiView||attrs.name||"";return name.indexOf("@")>=0?name:name+"@"+(inherited?inherited.state.name:"")}function stateContext(el){var stateData=el.parent().inheritedData("$uiView");if(stateData&&stateData.state&&stateData.state.name)return stateData.state}function $StateRefDirective($state,$timeout){var allowedOptions=["location","inherit","reload"];return{restrict:"A",require:["?^uiSrefActive","?^uiSrefActiveEq"],link:function(scope,element,attrs,uiSrefActive){var ref=function(ref,current){var parsed,preparsed=ref.match(/^\s*({[^}]*})\s*$/);if(preparsed&&(ref=current+"("+preparsed[1]+")"),!(parsed=ref.replace(/\n/g," ").match(/^([^(]+?)\s*(\((.*)\))?$/))||4!==parsed.length)throw new Error("Invalid state ref '"+ref+"'");return{state:parsed[1],paramExpr:parsed[3]||null}}(attrs.uiSref,$state.current.name),params=null,base=stateContext(element)||$state.$current,isForm="FORM"===element[0].nodeName,attr=isForm?"action":"href",nav=!0,options={relative:base,inherit:!0},optionsOverride=scope.$eval(attrs.uiSrefOpts)||{};angular.forEach(allowedOptions,(function(option){option in optionsOverride&&(options[option]=optionsOverride[option])}));var update=function(newVal){if(newVal&&(params=newVal),nav){var newHref=$state.href(ref.state,params,options),activeDirective=uiSrefActive[1]||uiSrefActive[0];if(activeDirective&&activeDirective.$$setStateInfo(ref.state,params),null===newHref)return nav=!1,!1;element[0][attr]=newHref}};ref.paramExpr&&(scope.$watch(ref.paramExpr,(function(newVal,oldVal){newVal!==params&&update(newVal)}),!0),params=scope.$eval(ref.paramExpr)),update(),isForm||element.bind("click",(function(e){if(!((e.which||e.button)>1||e.ctrlKey||e.metaKey||e.shiftKey||element.attr("target"))){var transition=$timeout((function(){$state.go(ref.state,params,options)}));e.preventDefault(),e.preventDefault=function(){$timeout.cancel(transition)}}}))}}}function $StateRefActiveDirective($state,$stateParams,$interpolate){return{restrict:"A",controller:["$scope","$element","$attrs",function($scope,$element,$attrs){var state,params,activeClass;function update(){(void 0!==$attrs.uiSrefActiveEq?$state.$current.self===state&&matchesParams():$state.includes(state.name)&&matchesParams())?$element.addClass(activeClass):$element.removeClass(activeClass)}function matchesParams(){return!params||equalForKeys(params,$stateParams)}activeClass=$interpolate($attrs.uiSrefActiveEq||$attrs.uiSrefActive||"",!1)($scope),this.$$setStateInfo=function(newState,newParams){state=$state.get(newState,stateContext($element)),params=newParams,update()},$scope.$on("$stateChangeSuccess",update)}]}}function $IsStateFilter($state){return function(state){return $state.is(state)}}function $IncludedByStateFilter($state){return function(state){return $state.includes(state)}}angular.module("ui.router.util",["ng"]),angular.module("ui.router.router",["ui.router.util"]),angular.module("ui.router.state",["ui.router.router","ui.router.util"]),angular.module("ui.router",["ui.router.state"]),angular.module("ui.router.compat",["ui.router"]),$Resolve.$inject=["$q","$injector"],angular.module("ui.router.util").service("$resolve",$Resolve),$TemplateFactory.$inject=["$http","$templateCache","$injector"],angular.module("ui.router.util").service("$templateFactory",$TemplateFactory),UrlMatcher.prototype.concat=function(pattern,config){return new UrlMatcher(this.sourcePath+pattern+this.sourceSearch,config)},UrlMatcher.prototype.toString=function(){return this.source},UrlMatcher.prototype.exec=function(path,searchParams){var m=this.regexp.exec(path);if(!m)return null;searchParams=searchParams||{};var i,cfg,param,params=this.parameters(),nTotal=params.length,nPath=this.segments.length-1,values={};if(nPath!==m.length-1)throw new Error("Unbalanced capture group in route '"+this.source+"'");for(i=0;i<nPath;i++)param=params[i],cfg=this.params[param],values[param]=cfg.$value(m[i+1]);for(;i<nTotal;i++)param=params[i],cfg=this.params[param],values[param]=cfg.$value(searchParams[param]);return values},UrlMatcher.prototype.parameters=function(param){return isDefined(param)?this.params[param]||null:objectKeys(this.params)},UrlMatcher.prototype.validates=function(params){var isOptional,cfg,result=!0,self=this;return forEach(params,(function(val,key){self.params[key]&&(cfg=self.params[key],isOptional=!val&&isDefined(cfg.value),result=result&&(isOptional||cfg.type.is(val)))})),result},UrlMatcher.prototype.format=function(values){var segments=this.segments,params=this.parameters();if(!values)return segments.join("").replace("//","/");var i,search,value,param,cfg,array,nPath=segments.length-1,nTotal=params.length,result=segments[0];if(!this.validates(values))return null;for(i=0;i<nPath;i++)value=values[param=params[i]],cfg=this.params[param],(isDefined(value)||"/"!==segments[i]&&"/"!==segments[i+1])&&(null!=value&&(result+=encodeURIComponent(cfg.type.encode(value))),result+=segments[i+1]);for(;i<nTotal;i++)null!=(value=values[param=params[i]])&&((array=isArray(value))&&(value=value.map(encodeURIComponent).join("&"+param+"=")),result+=(search?"&":"?")+param+"="+(array?value:encodeURIComponent(value)),search=!0);return result},UrlMatcher.prototype.$types={},Type.prototype.is=function(val,key){return!0},Type.prototype.encode=function(val,key){return val},Type.prototype.decode=function(val,key){return val},Type.prototype.equals=function(a,b){return a==b},Type.prototype.$subPattern=function(){var sub=this.pattern.toString();return sub.substr(1,sub.length-2)},Type.prototype.pattern=/.*/,angular.module("ui.router.util").provider("$urlMatcherFactory",$UrlMatcherFactory),$UrlRouterProvider.$inject=["$locationProvider","$urlMatcherFactoryProvider"],angular.module("ui.router.router").provider("$urlRouter",$UrlRouterProvider),$StateProvider.$inject=["$urlRouterProvider","$urlMatcherFactoryProvider"],angular.module("ui.router.state").value("$stateParams",{}).provider("$state",$StateProvider),$ViewProvider.$inject=[],angular.module("ui.router.state").provider("$view",$ViewProvider),angular.module("ui.router.state").provider("$uiViewScroll",(function(){var useAnchorScroll=!1;this.useAnchorScroll=function(){useAnchorScroll=!0},this.$get=["$anchorScroll","$timeout",function($anchorScroll,$timeout){return useAnchorScroll?$anchorScroll:function($element){$timeout((function(){$element[0].scrollIntoView()}),0,!1)}}]})),$ViewDirective.$inject=["$state","$injector","$uiViewScroll"],$ViewDirectiveFill.$inject=["$compile","$controller","$state"],angular.module("ui.router.state").directive("uiView",$ViewDirective),angular.module("ui.router.state").directive("uiView",$ViewDirectiveFill),$StateRefDirective.$inject=["$state","$timeout"],$StateRefActiveDirective.$inject=["$state","$stateParams","$interpolate"],angular.module("ui.router.state").directive("uiSref",$StateRefDirective).directive("uiSrefActive",$StateRefActiveDirective).directive("uiSrefActiveEq",$StateRefActiveDirective),$IsStateFilter.$inject=["$state"],$IncludedByStateFilter.$inject=["$state"],angular.module("ui.router.state").filter("isState",$IsStateFilter).filter("includedByState",$IncludedByStateFilter)}(window,window.angular);