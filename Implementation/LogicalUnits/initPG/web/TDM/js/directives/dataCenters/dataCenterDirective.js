function centerDataDirective(){return{restrict:"E",templateUrl:"views/dataCenters/dataCenter.html",scope:{content:"="},controller:function($scope,TDMService,BreadCrumbsService,SweetAlert,toastr,$timeout,AuthService){var dataCenterCtrl=this;dataCenterCtrl.dataCenterData=$scope.content.dataCenter,dataCenterCtrl.disableChange=!1,dataCenterCtrl.ipRegex=new RegExp(/\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b/),dataCenterCtrl.etlAddressOptions=[{id:1,name:"IP address"},{id:2,name:"Host name"}],dataCenterCtrl.etlAddress=2,dataCenterCtrl.ipRegex.test(dataCenterCtrl.dataCenterData.data_center_etl_ip_address)&&(dataCenterCtrl.etlAddress=1),dataCenterCtrl.disableChange="Inactive"==dataCenterCtrl.dataCenterData.data_center_status||!AuthService.authorizedToEdit(0),TDMService.getDataCenterEnvironmentCount(dataCenterCtrl.dataCenterData.data_center_id).then((function(response){"SUCCESS"==response.errorCode?dataCenterCtrl.dataCenterEnvironments=response.result.length:dataCenterCtrl.dataCenterEnvironments=0})),dataCenterCtrl.saveChanges=function(){1!=$scope.dataCenterForm.$invalid&&TDMService.updateDataCenter(dataCenterCtrl.dataCenterData.data_center_id,dataCenterCtrl.dataCenterData).then((function(response){"SUCCESS"==response.errorCode?(toastr.success("Data Center # "+dataCenterCtrl.dataCenterData.data_center_name,"Updated Successfully"),$timeout((function(){$scope.content.openDataCenters()}),400)):toastr.error("Data Center # "+dataCenterCtrl.dataCenterData.data_center_name,"failed to Update : "+response.message)}))},dataCenterCtrl.deleteDataCenter=function(){dataCenterCtrl.dataCenterEnvironments>0?SweetAlert.swal({title:"Product attached to this data center will be deleted from environments. Related tasks will also be deleted. Are you sure ?",type:"warning",showCancelButton:!0,confirmButtonColor:"#DD6B55",confirmButtonText:"No",cancelButtonText:"Yes",closeOnConfirm:!0,closeOnCancel:!0,animation:"false",customClass:"animated fadeInUp"},(function(isConfirm){isConfirm||TDMService.deleteDataCenter(dataCenterCtrl.dataCenterData.data_center_id).then((function(response){"SUCCESS"==response.errorCode?(toastr.success("Data Center # "+dataCenterCtrl.dataCenterData.data_center_name,"deleted Successfully"),$timeout((function(){$scope.content.openDataCenters()}),400)):toastr.error("Data Center # "+dataCenterCtrl.dataCenterData.data_center_name,"failed to delete")}))})):TDMService.deleteDataCenter(dataCenterCtrl.dataCenterData.data_center_id).then((function(response){"SUCCESS"==response.errorCode?(toastr.success("Data Center # "+dataCenterCtrl.dataCenterData.data_center_name,"deleted Successfully"),$timeout((function(){$scope.content.openDataCenters()}),400)):toastr.error("Data Center # "+dataCenterCtrl.dataCenterData.data_center_name,"failed to delete")}))},BreadCrumbsService.push({data_center_name:dataCenterCtrl.dataCenterData.data_center_name},"DATA_CENTER_BREADCRUMB",(function(){}))},controllerAs:"dataCenterCtrl"}}angular.module("TDM-FE").directive("centerDataDirective",centerDataDirective);