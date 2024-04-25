/**
 * angular-chosen-localytics - Angular Chosen directive is an AngularJS Directive that brings the Chosen jQuery in a Angular way
 * @version v1.5.1
 * @link http://github.com/leocaseiro/angular-chosen
 * @license MIT
 */
(function(){var e,n=[].indexOf||function(e){for(var n=0,t=this.length;n<t;n++)if(n in this&&this[n]===e)return n;return-1};angular.module("localytics.directives",[]),(e=angular.module("localytics.directives")).provider("chosen",(function(){var e;return e={},{setOption:function(n){angular.extend(e,n)},$get:function(){return e}}})),e.directive("chosen",["chosen","$timeout",function(t,r){var i,a,s,l;return a=/^\s*([\s\S]+?)(?:\s+as\s+([\s\S]+?))?(?:\s+group\s+by\s+([\s\S]+?))?\s+for\s+(?:([\$\w][\$\w]*)|(?:\(\s*([\$\w][\$\w]*)\s*,\s*([\$\w][\$\w]*)\s*\)))\s+in\s+([\s\S]+?)(?:\s+track\s+by\s+([\s\S]+?))?$/,i=["persistentCreateOption","createOptionText","createOption","skipNoResults","noResultsText","allowSingleDeselect","disableSearchThreshold","disableSearch","enableSplitWordSearch","inheritSelectClasses","maxSelectedOptions","placeholderTextMultiple","placeholderTextSingle","searchContains","singleBackstrokeDelete","displayDisabledOptions","displaySelectedOptions","width","includeGroupLabelInSelected","maxShownResults"],l=function(e){return e.replace(/[A-Z]/g,(function(e){return"_"+e.toLowerCase()}))},s=function(e){var n;if(angular.isArray(e))return 0===e.length;if(angular.isObject(e))for(n in e)if(e.hasOwnProperty(n))return!1;return!0},{restrict:"A",require:"?ngModel",priority:1,link:function(o,u,c,d){var f,h,p,v,b,y,S,w,O,m;if(o.disabledValuesHistory=o.disabledValuesHistory?o.disabledValuesHistory:[],(u=$(u)).addClass("localytics-chosen"),f=o.$eval(c.chosen)||{},v=angular.copy(t),angular.extend(v,f),angular.forEach(c,(function(e,t){if(n.call(i,t)>=0)return c.$observe(t,(function(e){var n;return n=String(u.attr(c.$attr[t])).slice(0,2),v[l(t)]="{{"===n?e:o.$eval(e),w()}))})),y=function(){return u.addClass("loading").attr("disabled",!0).trigger("chosen:updated")},S=function(){return u.removeClass("loading"),angular.isDefined(c.disabled)?u.attr("disabled",c.disabled):u.attr("disabled",!1),u.trigger("chosen:updated")},e=null,h=!1,p=function(){var t;if(e){if((t=$(u.parent()).find("div.chosen-drop"))&&t.length>0&&parseInt(t.css("left"))>=0)return;return u.trigger("chosen:updated")}if(o.$evalAsync((function(){e=u.chosen(v).data("chosen")})),angular.isObject(e))return e.default_text},w=function(){return e&&h?u.attr("data-placeholder",e.results_none_found).attr("disabled",!0):u.removeAttr("data-placeholder"),u.trigger("chosen:updated")},d?(b=d.$render,d.$render=function(){return b(),p()},u.on("chosen:hiding_dropdown",(function(){return o.$apply((function(){return d.$setTouched()}))})),c.multiple&&(m=function(){return d.$viewValue},o.$watch(m,d.$render,!0))):p(),c.$observe("disabled",(function(){return u.trigger("chosen:updated")})),c.ngOptions&&d)return O=c.ngOptions.match(a)[7],o.$watchCollection(O,(function(e,n){return r((function(){return angular.isUndefined(e)?y():(h=s(e),S(),w())}))})),o.$on("$destroy",(function(e){if("undefined"!=typeof timer&&null!==timer)return r.cancel(timer)}))}}}])}).call(this);