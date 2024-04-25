function taskHistoryDirective($interval){return{restrict:"EA",templateUrl:"views/tasks/tasksHistoryTable.html",scope:{content:"="},controller:function($scope,$rootScope,$window,$compile,$timeout,TDMService,AuthService,DTColumnBuilder,DTOptionsBuilder,DTColumnDefBuilder,$q,$sessionStorage,$http,toastr,$interval,$uibModal,ExcelService,$element){var taskHistoryTableCtrl=this;taskHistoryTableCtrl.taskData=$scope.content.task,taskHistoryTableCtrl.loadingTable=!0,$rootScope.inter_flag=!1,taskHistoryTableCtrl.userRole=AuthService.getRole(),taskHistoryTableCtrl.username=AuthService.getUsername(),taskHistoryTableCtrl.TDMReports=AuthService.getTDMReports(),taskHistoryTableCtrl.enableStopExecution=!1,taskHistoryTableCtrl.taskData.task_id,taskHistoryTableCtrl.taskHistoryData=[],taskHistoryTableCtrl.executionIds=[],taskHistoryTableCtrl.runningExecutions=[],String.prototype.replaceAll=function(search,replacement){return this.replace(new RegExp(search,"g"),replacement)},"Active"==taskHistoryTableCtrl.taskData.task_status&&("admin"==taskHistoryTableCtrl.userRole.type||taskHistoryTableCtrl.taskData.owners.indexOf(taskHistoryTableCtrl.username)>=0||taskHistoryTableCtrl.username==taskHistoryTableCtrl.taskData.task_created_by)&&(taskHistoryTableCtrl.enableStopExecution=!0),taskHistoryTableCtrl.disableAccessingStatistics=!!(taskHistoryTableCtrl.taskData.disabled||taskHistoryTableCtrl.taskData.onHold||taskHistoryTableCtrl.taskData.executioncount);const updateIdsForSort=(data,item,prefix)=>{item.id=`${prefix}${""===prefix?"":"_"}${item.lu_id}`,children=taskHistoryTableCtrl.taskHistoryData.filter(it=>it.lu_parent_name===item.lu_name),children.forEach(it=>{updateIdsForSort(data,it,item.id)})};taskHistoryTableCtrl.getData=running=>{taskHistoryTableCtrl.loadingTable=!0,TDMService.getTaskHistory($scope.content.exec_id).then((function(response){if("SUCCESS"!=response.errorCode)return;taskHistoryTableCtrl.taskType="LOAD",response.result&&response.result.length>0&&(taskHistoryTableCtrl.taskType=response.result[0].task_type||"LOAD"),taskHistoryTableCtrl.taskHistoryData=response.result;const runningExecutions=taskHistoryTableCtrl.taskHistoryData.filter(execution=>"RUNNING"==execution.execution_status.toUpperCase()||"EXECUTING"==execution.execution_status.toUpperCase()||"STARTED"==execution.execution_status.toUpperCase()||"STARTEXECUTIONREQUESTED"==execution.execution_status.toUpperCase()),pendingTaskIndex=taskHistoryTableCtrl.taskHistoryData.findIndex(execution=>"PENDING"===execution.execution_status.toUpperCase()),pendingTasks=pendingTaskIndex>=0;let runningExecution=null;if(runningExecutions.length>0)runningExecution=runningExecutions[0].task_execution_id;else if(pendingTaskIndex>=0)taskHistoryTableCtrl.runningExecution=taskHistoryTableCtrl.taskHistoryData[pendingTaskIndex].task_execution_id;else{const pausedTasks=taskHistoryTableCtrl.taskHistoryData.findIndex(execution=>"PAUSED"===execution.execution_status.toUpperCase());pausedTasks>=0&&(taskHistoryTableCtrl.runningExecution=taskHistoryTableCtrl.taskHistoryData[pausedTasks].task_execution_id)}var data;(data=taskHistoryTableCtrl.taskHistoryData).forEach(item=>{item.pre_process_name="",item.post_process_name="","pre"===item.process_type?(item.id="0_"+item.execution_order,item.pre_process_name=item.process_name):"post"===item.process_type?(item.id="999999999999999999999999_"+item.execution_order,item.post_process_name=item.process_name):item.lu_name||(item.id="888888888888888888888888",item.post_process_name=item.process_name),item.pre_process_name||item.post_process_name||item.lu_parent_name||!item.lu_name||updateIdsForSort(data,item,"")}),taskHistoryTableCtrl.taskHistoryData=taskHistoryTableCtrl.taskHistoryData.sort((item1,item2)=>(""+item1.id).localeCompare(item2.id)),taskHistoryTableCtrl.dtInstance.reloadData((function(data){}),!1);const executionIds=_.map(runningExecutions,(function(execution){return{name:execution.post_process_name||execution.pre_process_name||execution.lu_name,etl_execution_id:execution.etl_execution_id,etl_ip_address:execution.etl_ip_address,fabric_execution_id:execution.fabric_execution_id,task_execution_id:execution.task_execution_id,post_process_name:execution.post_process_name,pre_process_name:execution.pre_process_name}}));taskHistoryTableCtrl.disableAccessingStatistics=!!executionIds.length,pendingTasks&&(taskHistoryTableCtrl.disableAccessingStatistics=!0),taskHistoryTableCtrl.runningExecutions=runningExecutions,taskHistoryTableCtrl.runningExecution=runningExecution,taskHistoryTableCtrl.executionIds=executionIds,pendingTasks&&0==taskHistoryTableCtrl.runningExecutions.length?$timeout((function(){taskHistoryTableCtrl.getData()}),2e3):taskHistoryTableCtrl.executionIds.length>0&&!running&&$scope.updateRunningExecutions(),$timeout(()=>{taskHistoryTableCtrl.loadingTable=!1})}))},taskHistoryTableCtrl.dtInstance={},taskHistoryTableCtrl.dtColumns=[],taskHistoryTableCtrl.dtColumnDefs=[],taskHistoryTableCtrl.headers=[{column:"lu_name",name:"Logical Unit Name",clickAble:!1},{column:"lu_parent_name",name:"Parent Logical Unit",clickAble:!1},{column:"pre_process_name",name:"Pre Execution Process Name",clickAble:!1},{column:"post_process_name",name:"Post Execution Process Name",clickAble:!1},{column:"execution_status",name:"Execution Status",clickAble:!1},{column:"num_of_processed_entities",name:"Total Number Of Processed Entities",clickAble:!1},{column:"num_of_copied_entities",name:"Number Of Succeeded Entities",clickAble:!1},{column:"num_of_failed_entities",name:"Number Of Failed Entities",clickAble:!1},{column:"num_of_processed_ref_tables",name:"Total Number Of Processed Tables",clickAble:!1},{column:"num_of_copied_ref_tables",name:"Number Of Copied Tables",clickAble:!1},{column:"num_of_failed_ref_tables",name:"Number Of Failed Tables",clickAble:!1},{column:"start_execution_time",name:"Start Execution Date",clickAble:!1,date:!0},{column:"end_execution_time",name:"End Execution Date",clickAble:!1,date:!0},{column:"source_env_name",name:"Source Environment Name",clickAble:!1},{column:"environment_name",name:"Target Environment Name",clickAble:!1},{column:"task_executed_by",name:"Task Executed By",clickAble:!0},{column:"be_name",name:"Business Entity Name",clickAble:!1},{column:"product_name",name:"System Name",clickAble:!1},{column:"product_version",name:"System Version",clickAble:!1},{column:"fabric_execution_id",name:"Fabric Execution Id",clickAble:!1,visible:!1},{column:"version_expiration_date",name:"Version Expiration Date",clickAble:!1,date:!0},{column:"execution_note",name:"Execution Note",clickAble:!1,date:!1}],taskHistoryTableCtrl.dtColumnDefs=[],$sessionStorage.taskHistoryTableHideColumns?taskHistoryTableCtrl.hideColumns=$sessionStorage.taskHistoryTableHideColumns:taskHistoryTableCtrl.hideColumns=[19];for(var i=0;i<taskHistoryTableCtrl.hideColumns.length;i++){var hideColumn=DTColumnDefBuilder.newColumnDef(taskHistoryTableCtrl.hideColumns[i]).withOption("visible",!1);taskHistoryTableCtrl.dtColumnDefs.push(hideColumn)}var changeToLocalDate=function(data,type,full,meta){return data?moment(data).format("DD MMM YYYY, HH:mm:ss"):""};for(i=0;i<taskHistoryTableCtrl.headers.length;i++)1==taskHistoryTableCtrl.headers[i].date?taskHistoryTableCtrl.dtColumns.push(DTColumnBuilder.newColumn(taskHistoryTableCtrl.headers[i].column).withTitle(taskHistoryTableCtrl.headers[i].name).renderWith(changeToLocalDate)):taskHistoryTableCtrl.dtColumns.push(DTColumnBuilder.newColumn(taskHistoryTableCtrl.headers[i].column).withTitle(taskHistoryTableCtrl.headers[i].name));taskHistoryTableCtrl.dtColumns.unshift(DTColumnBuilder.newColumn("taskHistoryActions").withTitle("").renderWith((function(data,type,full,meta){if(full.process_id)return"";var pathfile=taskHistoryTableCtrl.TDMReports.replace("[etlIpAddress]",full.etl_ip_address);full.etl_execution_id;var fileName=full.lu_name+"_Stats_Report_EXEID_"+full.etl_execution_id+".csv",seqName=full.lu_name+"_Sequences_Report_EXEID_"+full.etl_execution_id+".csv";taskHistoryTableCtrl.statsFile=pathfile+fileName,taskHistoryTableCtrl.seqFile=pathfile+seqName;var taskHistoryActions="";return!full.execution_status||"stopped"!=full.execution_status.toLowerCase()||taskHistoryTableCtrl.executionIds&&0!=taskHistoryTableCtrl.executionIds.length||(taskHistoryActions=taskHistoryActions+"<a ng-click=\"taskHistoryTableCtrl.resumeExecution('"+full.fabric_execution_id+"','"+full.task_execution_id+'\')" style="margin-left: 5px;border-color: transparent;background-color: transparent; color: black;" type="button" title="Resume Execution"><i class="fa fa-play"></i> </a>'),taskHistoryActions=taskHistoryTableCtrl.runningExecution==full.task_execution_id?taskHistoryActions='<a  style="margin-left: 3px;border-color: transparent;background-color: transparent; color: grey;cursor: not-allowed;"  type="button" title="Download Statistics File"><img src="icons/summary-report.svg"></img> </a>':taskHistoryActions+"<a ng-click=\"taskHistoryTableCtrl.downloadErrorReport('"+full.lu_name+"',"+full.task_execution_id+')"  style="margin-left: 3px;border-color: transparent;background-color: transparent; color: black;" title="Download Summary Report"><img src="icons/summary-report.svg"></img> </a>',full.fabric_execution_id&&(taskHistoryActions=taskHistoryActions+"<a ng-click=\"taskHistoryTableCtrl.openBatchMonitor('"+full.fabric_execution_id+'\')"  style="margin-left: 3px;border-color: transparent;background-color: transparent; color: black !important;font-size: 22px;" title="Open Batch Monitor"><i class="fa fa-info-circle"></i> </a>'),taskHistoryActions})).withOption("width","100"));var getTableData=function(){var deferred=$q.defer();const data=_.map(taskHistoryTableCtrl.taskHistoryData,row=>("EXTRACT"===taskHistoryTableCtrl.taskType&&(row.environment_name=""),row));return deferred.resolve(data),deferred.promise};if(taskHistoryTableCtrl.dtOptions=DTOptionsBuilder.fromFnPromise((function(){return getTableData()})).withDOM('<"html5buttons"B>lTfgitp').withOption("createdRow",(function(row){$compile(angular.element(row).contents())($scope)})).withOption("paging",!0).withOption("scrollX",!1).withOption("aaSorting",[]).withButtons([{extend:"colvis",text:"Show/Hide columns",columns:[5,6,7,8,9,10,16,17,18,19],callback:function(columnIdx,visible){var index;1==visible?(index=taskHistoryTableCtrl.hideColumns.indexOf(columnIdx))>=0&&taskHistoryTableCtrl.hideColumns.splice(index,1):(index=taskHistoryTableCtrl.hideColumns.indexOf(columnIdx))<0&&taskHistoryTableCtrl.hideColumns.push(columnIdx);$sessionStorage.taskHistoryTableHideColumns=taskHistoryTableCtrl.hideColumns}}]),taskHistoryTableCtrl.taskHistoryData&&taskHistoryTableCtrl.taskHistoryData.length>0){const columns=[{type:"select",values:_.map(_.unique(_.map(taskHistoryTableCtrl.taskHistoryData,"lu_name")),(function(el){return{value:el,label:el}}))},{type:"select",values:_.map(_.unique(_.map(taskHistoryTableCtrl.taskHistoryData,"lu_parent_name")),(function(el){return{value:el,label:el}}))},{type:"select",values:_.map(_.unique(_.map(taskHistoryTableCtrl.taskHistoryData,"pre_process_name")),(function(el){return{value:el,label:el}}))},{type:"select",values:_.map(_.unique(_.map(taskHistoryTableCtrl.taskHistoryData,"post_process_name")),(function(el){return{value:el,label:el}}))},{type:"select",values:_.map(_.unique(_.map(taskHistoryTableCtrl.taskHistoryData,"execution_status")),(function(el){return{value:el,label:el}}))},{type:"text"},{type:"text"},{type:"text"},{type:"text"},{type:"text"},{type:"text"},{type:"text"},{type:"text"},{type:"select",values:_.map(_.unique(_.map(taskHistoryTableCtrl.taskHistoryData,"source_env_name")),(function(el){return{value:el,label:el}}))},{type:"select",values:_.map(_.unique(_.map(taskHistoryTableCtrl.taskHistoryData,"environment_name")),(function(el){return{value:el,label:el}}))},{type:"select",values:_.map(_.unique(_.map(taskHistoryTableCtrl.taskHistoryData,"task_executed_by")),(function(el){return{value:el,label:el}}))},{type:"select",values:_.map(_.unique(_.map(taskHistoryTableCtrl.taskHistoryData,"be_name")),(function(el){return{value:el,label:el}}))},{type:"select",values:_.map(_.unique(_.map(taskHistoryTableCtrl.taskHistoryData,"product_name")),(function(el){return{value:el,label:el}}))},{type:"text"},{type:"text"},{type:"text"},{type:"text"}],lightColumnFilter={};columns.forEach((column,index)=>{let temp=angular.copy(column);temp.hidden=taskHistoryTableCtrl.hideColumns.indexOf(index+1)>=0,lightColumnFilter[index+1]=temp}),taskHistoryTableCtrl.dtOptions.withLightColumnFilter(lightColumnFilter)}taskHistoryTableCtrl.dtInstanceCallback=function(dtInstance){angular.isFunction(taskHistoryTableCtrl.dtInstance)?taskHistoryTableCtrl.dtInstance(dtInstance):angular.isDefined(taskHistoryTableCtrl.dtInstance)&&(taskHistoryTableCtrl.dtInstance=dtInstance)},null!=taskHistoryTableCtrl.dtInstance.changeData&&taskHistoryTableCtrl.dtInstance.changeData(getTableData()),taskHistoryTableCtrl.openEntitiesExecStats=function(taskExecId,type,fabricExecutionId,selectionMethod,refcount){taskHistoryTableCtrl.disableAccessingStatistics||$scope.$parent.$parent.$parent.tasks.openEntitiesExecStats(taskExecId,type,fabricExecutionId,selectionMethod,refcount)},taskHistoryTableCtrl.openBatchMonitor=batchId=>{$window.open(`${location.origin}/app/admin/batches/batch-monitor/${batchId}`,"_blank")},taskHistoryTableCtrl.downloadErrorReport=function(lu_name,taskExecutionId){TDMService.getSummaryReport(taskExecutionId,lu_name).then((function(response){"FAILED"!=response.errorCode&&ExcelService.buildSummaryExcel(response.result).xlsx.writeBuffer().then((function(data){var fileName=`Summary_Report_EXECID_${taskExecutionId}_${lu_name}.xlsx`,a=document.createElement("a");document.body.appendChild(a),a.style="display: none";var file=new Blob([data],{type:"application/vnd.ms-excel"}),fileURL=(window.URL||window.webkitURL).createObjectURL(file);a.href=fileURL,a.download=fileName,a.click(),(window.URL||window.webkitURL).revokeObjectURL(file)}))}))},$scope.getMigrateStatusWs=function(){if(taskHistoryTableCtrl.executionIds&&taskHistoryTableCtrl.executionIds.length>0&&!taskHistoryTableCtrl.executionIds[0].fabric_execution_id&&(taskHistoryTableCtrl.executionIds[0].post_process_name||taskHistoryTableCtrl.executionIds[0].pre_process_name))return taskHistoryTableCtrl.executionData=[{name:taskHistoryTableCtrl.executionIds[0].post_process_name||taskHistoryTableCtrl.executionIds[0].pre_process_name,migrateID:taskHistoryTableCtrl.executionIds[0].fabric_execution_id,migrationCommand:"",remainingDuration:"00:00:00",percentageOfCompleted:"0%",added:0,failed:0,Updated:0,Unchanged:0,processed:0}],void $timeout((function(){taskHistoryTableCtrl.reloadData()}),5e3);TDMService.postGenericAPI("migrateStatusWs",{migrateIds:_.map(_.filter(taskHistoryTableCtrl.executionIds,it=>it.fabric_execution_id),"fabric_execution_id"),runModes:["H","S"]}).then((function(response){taskHistoryTableCtrl.executionData=[];let finished=!1,i=0;for(res of response.result){if(taskHistoryTableCtrl.migrationCommand="",res&&res.H&&res.H["Migration Command"]&&(taskHistoryTableCtrl.migrationCommand=res.H["Migration Command"]),taskHistoryTableCtrl.clusterLevel={},res&&res.S&&res.S.results){var clusterLevel=_.find(res.S.results,{columns:{Level:"Cluster"}});clusterLevel&&(taskHistoryTableCtrl.clusterLevel=clusterLevel.columns,"DONE"!=taskHistoryTableCtrl.clusterLevel.Status&&"FAILED"!=taskHistoryTableCtrl.clusterLevel.Status||(finished=!0))}else finished=!0;var added=("LOAD"===taskHistoryTableCtrl.taskType?taskHistoryTableCtrl.clusterLevel.Succeeded:taskHistoryTableCtrl.clusterLevel.Added)||0;taskHistoryTableCtrl.executionData.push({name:taskHistoryTableCtrl.executionIds&&taskHistoryTableCtrl.executionIds[i]&&(taskHistoryTableCtrl.executionIds[i].post_process_name||taskHistoryTableCtrl.executionIds[i].pre_process_name||taskHistoryTableCtrl.executionIds[i].name),migrateID:taskHistoryTableCtrl.executionIds&&taskHistoryTableCtrl.executionIds[i]&&taskHistoryTableCtrl.executionIds[i].fabric_execution_id,migrationCommand:taskHistoryTableCtrl.migrationCommand,remainingDuration:taskHistoryTableCtrl.clusterLevel["Remaining dur."]||"00:00:00",percentageOfCompleted:taskHistoryTableCtrl.clusterLevel["% Completed"],added:added||0,failed:taskHistoryTableCtrl.clusterLevel.Failed||0,Updated:taskHistoryTableCtrl.clusterLevel.Updated||0,Unchanged:taskHistoryTableCtrl.clusterLevel.Unchanged||0,processed:parseInt(added||0)+parseInt(taskHistoryTableCtrl.clusterLevel.Failed||0)+parseInt(taskHistoryTableCtrl.clusterLevel.Updated||0)+parseInt(taskHistoryTableCtrl.clusterLevel.Unchanged||0)}),i++}finished?$timeout((function(){taskHistoryTableCtrl.reloadData()}),2e3):(taskHistoryTableCtrl.reloadData(!0),$timeout(()=>{$scope.updateRunningExecutions()},2e3))}))},taskHistoryTableCtrl.startExtractRefStatsDetailed=function(type,lu_name){if(!taskHistoryTableCtrl.executionIds||0==taskHistoryTableCtrl.executionIds.length)return;let stopInterval;var task_execution_id=taskHistoryTableCtrl.executionIds[0].task_execution_id;$uibModal.open({templateUrl:"views/tasks/taskHistoryRefModal.html",windowTopClass:"taskHistoryRef",controller:function($scope,$uibModalInstance,TDMService){var taskHistoryRefCtrl=this;taskHistoryRefCtrl.getExtractRefStats=function(){TDMService.postGenericAPI("extractrefstats",{taskExecutionId:task_execution_id,type:"D"}).then((function(response){taskHistoryRefCtrl.refDetailedData=_.map(response.result||[],(function(refData){return refData.number_of_records_to_process>0&&(refData.percentageOfCompleted=refData.number_of_processed_records/refData.number_of_records_to_process*100),isNaN(refData.number_of_records_to_process)||(refData.number_of_records_to_process=refData.number_of_records_to_process.toString().replace(/\B(?=(\d{3})+(?!\d))/g,",")),isNaN(refData.number_of_processed_records)||(refData.number_of_processed_records=refData.number_of_processed_records.toString().replace(/\B(?=(\d{3})+(?!\d))/g,",")),refData})),taskHistoryRefCtrl.refDetailedData=_.filter(taskHistoryRefCtrl.refDetailedData,(function(data){return!(!data||data.lu_name!==lu_name)&&("failed"==type?"failed"==data.execution_status:"failed"!=type?"failed"!=data.execution_status:void 0)}))}))},stopInterval=$interval((function(){taskHistoryRefCtrl.getExtractRefStats()}),1e4),taskHistoryRefCtrl.getExtractRefStats(),taskHistoryRefCtrl.close=function(){$uibModalInstance.close()}},controllerAs:"taskHistoryRefCtrl"}).result.then((function(){stopInterval&&$interval.cancel(stopInterval)}),(function(){stopInterval&&$interval.cancel(stopInterval)}))},taskHistoryTableCtrl.reloadData=function(running){taskHistoryTableCtrl.getData(running)},taskHistoryTableCtrl.stopExecution=function(executionId){0!=taskHistoryTableCtrl.executionIds.length&&TDMService.postGenericAPI("cancelMigratWS",{taskExecutionId:taskHistoryTableCtrl.executionIds[0].task_execution_id}).then((function(response){taskHistoryTableCtrl.executionIds=[],$timeout((function(){taskHistoryTableCtrl.reloadData()}),3e3)}))},taskHistoryTableCtrl.resumeExecution=function(migrateId,task_execution_id){task_execution_id&&TDMService.postGenericAPI("resumeMigratWS",{taskExecutionId:task_execution_id}).then((function(response){toastr.success("Task Execution # "+task_execution_id," Successfully Resumed"),$timeout((function(){$rootScope.$broadcast("refreshPage",!0)}),5e3)}))},$scope.updateRunningExecutions=function(){taskHistoryTableCtrl.executionIds.length>0?(taskHistoryTableCtrl.disableAccessingStatistics=!0,"TABLES"!==taskHistoryTableCtrl.taskData.selection_method&&$scope.getMigrateStatusWs(),("TABLES"===taskHistoryTableCtrl.taskData.selection_method||taskHistoryTableCtrl.taskData.refcount>0)&&TDMService.postGenericAPI("extractrefstats",{taskExecutionId:taskHistoryTableCtrl.executionIds[0].task_execution_id,type:"S"}).then((function(response){taskHistoryTableCtrl.executionRefData=[];var executionsFinished=!1;for(key in response.result){let refExecution=response.result[key];refExecution.lu_name=key,refExecution.percentageOfCompleted=0,refExecution.totNumOfTablesToProcess>0&&(refExecution.percentageOfCompleted=refExecution.numOfProcessedRefTables/refExecution.totNumOfTablesToProcess*100),100==refExecution.percentageOfCompleted&&(executionsFinished=!0),refExecution.percentageOfCompleted=refExecution.percentageOfCompleted.toFixed(2),taskHistoryTableCtrl.executionRefData.push(refExecution)}1==executionsFinished?(taskHistoryTableCtrl.disableAccessingStatistics=!1,$timeout((function(){taskHistoryTableCtrl.reloadData()}),2e3)):(taskHistoryTableCtrl.reloadData(!0),$timeout((function(){$scope.updateRunningExecutions()}),2e3))}))):taskHistoryTableCtrl.disableAccessingStatistics=!1},taskHistoryTableCtrl.getData()},controllerAs:"taskHistoryTableCtrl"}}angular.module("TDM-FE").directive("taskHistoryDirective",taskHistoryDirective);