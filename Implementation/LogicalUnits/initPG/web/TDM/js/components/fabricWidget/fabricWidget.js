function fabricWidget(){return{restrict:"EA",templateUrl:"js/components/fabricWidget/fabricWidget.html",scope:{editor:"=",data:"=",luName:"=",flowName:"="},controller:function($scope,$element){let editor=$scope.editor;Array.isArray(editor)||(editor=[editor]),window.k2widgets.createWidget($element[0],"plugins",data=>{$scope.data.refData=data},void 0,{plugins:editor,theme:"light",luName:$scope.luName,flowName:$scope.flowName})},controllerAs:"fabricWidgetCtrl"}}angular.module("TDM-FE").directive("fabricWidget",fabricWidget);