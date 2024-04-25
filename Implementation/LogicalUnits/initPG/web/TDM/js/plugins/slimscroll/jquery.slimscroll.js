/*! Copyright (c) 2011 Piotr Rochala (http://rocha.la)
 * Dual licensed under the MIT (http://www.opensource.org/licenses/mit-license.php)
 * and GPL (http://www.opensource.org/licenses/gpl-license.php) licenses.
 *
 * Version: 1.3.6
 *
 */
!function($){$.fn.extend({slimScroll:function(options){var o=$.extend({width:"auto",height:"250px",size:"7px",color:"#000",position:"right",distance:"1px",start:"top",opacity:.4,alwaysVisible:!1,disableFadeOut:!1,railVisible:!1,railColor:"#333",railOpacity:.2,railDraggable:!0,railClass:"slimScrollRail",barClass:"slimScrollBar",wrapperClass:"slimScrollDiv",allowPageScroll:!1,wheelStep:20,touchScrollStep:200,borderRadius:"7px",railBorderRadius:"7px"},options);return this.each((function(){var isOverPanel,isOverBar,isDragg,queueHide,touchDif,barHeight,percentScroll,lastScroll,divS="<div></div>",releaseScroll=!1,me=$(this);if(me.parent().hasClass(o.wrapperClass)){var offset=me.scrollTop();if(bar=me.closest("."+o.barClass),rail=me.closest("."+o.railClass),getBarHeight(),$.isPlainObject(options)){if("height"in options&&"auto"==options.height){me.parent().css("height","auto"),me.css("height","auto");var height=me.parent().parent().height();me.parent().css("height",height),me.css("height",height)}if("scrollTo"in options)offset=parseInt(o.scrollTo);else if("scrollBy"in options)offset+=parseInt(o.scrollBy);else if("destroy"in options)return bar.remove(),rail.remove(),void me.unwrap();scrollContent(offset,!1,!0)}}else if(!$.isPlainObject(options)||!("destroy"in options)){o.height="auto"==o.height?me.parent().height():o.height;var wrapper=$(divS).addClass(o.wrapperClass).css({position:"relative",overflow:"hidden",width:o.width,height:o.height});me.css({overflow:"hidden",width:o.width,height:o.height});var target,rail=$(divS).addClass(o.railClass).css({width:o.size,height:"100%",position:"absolute",top:0,display:o.alwaysVisible&&o.railVisible?"block":"none","border-radius":o.railBorderRadius,background:o.railColor,opacity:o.railOpacity,zIndex:90}),bar=$(divS).addClass(o.barClass).css({background:o.color,width:o.size,position:"absolute",top:0,opacity:o.opacity,display:o.alwaysVisible?"block":"none","border-radius":o.borderRadius,BorderRadius:o.borderRadius,MozBorderRadius:o.borderRadius,WebkitBorderRadius:o.borderRadius,zIndex:99}),posCss="right"==o.position?{right:o.distance}:{left:o.distance};rail.css(posCss),bar.css(posCss),me.wrap(wrapper),me.parent().append(bar),me.parent().append(rail),o.railDraggable&&bar.bind("mousedown",(function(e){var $doc=$(document);return isDragg=!0,t=parseFloat(bar.css("top")),pageY=e.pageY,$doc.bind("mousemove.slimscroll",(function(e){currTop=t+e.pageY-pageY,bar.css("top",currTop),scrollContent(0,bar.position().top,!1)})),$doc.bind("mouseup.slimscroll",(function(e){isDragg=!1,hideBar(),$doc.unbind(".slimscroll")})),!1})).bind("selectstart.slimscroll",(function(e){return e.stopPropagation(),e.preventDefault(),!1})),rail.hover((function(){showBar()}),(function(){hideBar()})),bar.hover((function(){isOverBar=!0}),(function(){isOverBar=!1})),me.hover((function(){isOverPanel=!0,showBar(),hideBar()}),(function(){isOverPanel=!1,hideBar()})),me.bind("touchstart",(function(e,b){e.originalEvent.touches.length&&(touchDif=e.originalEvent.touches[0].pageY)})),me.bind("touchmove",(function(e){(releaseScroll||e.originalEvent.preventDefault(),e.originalEvent.touches.length)&&(scrollContent((touchDif-e.originalEvent.touches[0].pageY)/o.touchScrollStep,!0),touchDif=e.originalEvent.touches[0].pageY)})),getBarHeight(),"bottom"===o.start?(bar.css({top:me.outerHeight()-bar.outerHeight()}),scrollContent(0,!0)):"top"!==o.start&&(scrollContent($(o.start).position().top,null,!0),o.alwaysVisible||bar.hide()),target=this,window.addEventListener?(target.addEventListener("DOMMouseScroll",_onWheel,!1),target.addEventListener("mousewheel",_onWheel,!1)):document.attachEvent("onmousewheel",_onWheel)}function _onWheel(e){if(isOverPanel){var delta=0;(e=e||window.event).wheelDelta&&(delta=-e.wheelDelta/120),e.detail&&(delta=e.detail/3);var target=e.target||e.srcTarget||e.srcElement;$(target).closest("."+o.wrapperClass).is(me.parent())&&scrollContent(delta,!0),e.preventDefault&&!releaseScroll&&e.preventDefault(),releaseScroll||(e.returnValue=!1)}}function scrollContent(y,isWheel,isJump){releaseScroll=!1;var delta=y,maxTop=me.outerHeight()-bar.outerHeight();if(isWheel&&(delta=parseInt(bar.css("top"))+y*parseInt(o.wheelStep)/100*bar.outerHeight(),delta=Math.min(Math.max(delta,0),maxTop),delta=y>0?Math.ceil(delta):Math.floor(delta),bar.css({top:delta+"px"})),delta=(percentScroll=parseInt(bar.css("top"))/(me.outerHeight()-bar.outerHeight()))*(me[0].scrollHeight-me.outerHeight()),isJump){var offsetTop=(delta=y)/me[0].scrollHeight*me.outerHeight();offsetTop=Math.min(Math.max(offsetTop,0),maxTop),bar.css({top:offsetTop+"px"})}me.scrollTop(delta),me.trigger("slimscrolling",~~delta),showBar(),hideBar()}function getBarHeight(){barHeight=Math.max(me.outerHeight()/me[0].scrollHeight*me.outerHeight(),30),bar.css({height:barHeight+"px"});var display=barHeight==me.outerHeight()?"none":"block";bar.css({display:display})}function showBar(){if(getBarHeight(),clearTimeout(queueHide),percentScroll==~~percentScroll){if(releaseScroll=o.allowPageScroll,lastScroll!=percentScroll){var msg=0==~~percentScroll?"top":"bottom";me.trigger("slimscroll",msg)}}else releaseScroll=!1;lastScroll=percentScroll,barHeight>=me.outerHeight()?releaseScroll=!0:(bar.stop(!0,!0).fadeIn("fast"),o.railVisible&&rail.stop(!0,!0).fadeIn("fast"))}function hideBar(){o.alwaysVisible||(queueHide=setTimeout((function(){o.disableFadeOut&&isOverPanel||isOverBar||isDragg||(bar.fadeOut("slow"),rail.fadeOut("slow"))}),1e3))}})),this}}),$.fn.extend({slimscroll:$.fn.slimScroll})}(jQuery);