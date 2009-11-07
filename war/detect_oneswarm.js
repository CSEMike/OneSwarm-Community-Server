
var reloaded = false;
var loc=""+document.location;
var sep = loc.indexOf("?") == -1 ? "?" : "&"; 
if( loc.indexOf("?reloaded") != -1 || 
	loc.indexOf("&reloaded") != -1 ) { 
	reloaded=true
}
function reloadOnceOnly() {
	if (!reloaded) {
		window.location.replace(window.location+sep+"reloaded");
	}
}

function readCookie(name) {
	var nameEQ = name + "=";
	var ca = document.cookie.split(';');
	for(var i=0;i < ca.length;i++) {
		var c = ca[i];
		while (c.charAt(0)==' ') c = c.substring(1,c.length);
		if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);
	}
	return null;
}

function checkIfOneSwarmRunning() {
		
	if( readCookie('oneswarm_running') != null ) {
		return;
	}
	
	var ONESWARM_HOST = '127.0.0.1'
	var ONESWARM_PORT = 29615
	
	var img = new Image();
		
	img.onerror = function(evt) {
		// nothing
	}
	img.onload = function(evt) {
		var date = new Date();
		date.setTime(date.getTime()+1*1000*60)
		var expires = "; expires="+date.toGMTString();
		
		document.cookie = "oneswarm_running=1"+expires+";path=/";
		
		reloadOnceOnly();
		//location.reload()
	}
	
	img.src = "http://" + ONESWARM_HOST + ":" + ONESWARM_PORT + "/oneswarmgwt/1by1.jpg";

} // checkIfOneSwarmRunning

checkIfOneSwarmRunning();

