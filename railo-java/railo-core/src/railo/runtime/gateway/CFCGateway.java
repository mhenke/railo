package railo.runtime.gateway;

import java.util.Map;

import org.opencfml.eventgateway.Gateway;
import org.opencfml.eventgateway.GatewayEngine;
import org.opencfml.eventgateway.GatewayException;

import railo.commons.lang.StringUtil;
import railo.runtime.exp.PageException;
import railo.runtime.op.Caster;
import railo.runtime.op.Constants;
import railo.runtime.type.Struct;
import railo.runtime.type.StructImpl;

public class CFCGateway implements Gateway {
	
	//private static final Object OBJ = new Object();
	//private Component _cfc;
	private String id;
	private int state=Gateway.STOPPED;
	private String cfcPath;
	//private Config config;
	//private String requestURI;
	//private Resource cfcDirectory;
	private GatewayEngineImpl engine;

	public CFCGateway(String cfcPath) {
		this.cfcPath=cfcPath;
	}

	/**
	 * @see org.opencfml.eventgateway.Gateway#init(java.lang.String, java.lang.String, railo.runtime.type.Struct)
	 */
	public void init(GatewayEngine engine,String id, String cfcPath, Map config) throws GatewayException {
		this.engine=(GatewayEngineImpl) engine;
		this.id=id;
		
		//requestURI=engine.toRequestURI(cfcPath);
		Struct args=new StructImpl(StructImpl.TYPE_LINKED);
		args.setEL("config", Caster.toStruct(config,null,false));
		if(!StringUtil.isEmpty(cfcPath)){
			try {
				args.setEL("listener", this.engine.getComponent(cfcPath,id));
			} catch (PageException e) {
				engine.log(this,GatewayEngine.LOGLEVEL_ERROR, e.getMessage());
			}
		}
		
		try {
			callOneWay("init",args);
		} catch (PageException pe) {
			
			engine.log(this,GatewayEngine.LOGLEVEL_ERROR, pe.getMessage());
			//throw new PageGatewayException(pe);
		}
		
	}

	/**
	 * @see org.opencfml.eventgateway.Gateway#doRestart()
	 */
	public void doRestart() throws GatewayException {

		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"restart");
		Struct args=new StructImpl();
		try{
			boolean has=callOneWay("restart",args);
			if(!has){
				if(callOneWay("stop",args))
					callOneWay("start",args);
			}
		}
		catch(PageException pe){ 
			throw new PageGatewayException(pe);
		}
		
	}

	/**
	 * @see org.opencfml.eventgateway.Gateway#doStart()
	 */
	public void doStart() throws GatewayException {
		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"start");
		Struct args=new StructImpl();
		state=STARTING;
		try{
			callOneWay("start",args);
			engine.log(this,GatewayEngine.LOGLEVEL_INFO,"running");
			state=RUNNING;
		}
		catch(PageException pe){
			state=FAILED;
			throw new PageGatewayException(pe);
		}
	}

	/**
	 * @see org.opencfml.eventgateway.Gateway#doStop()
	 */
	public void doStop() throws GatewayException {

		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"stop");
		Struct args=new StructImpl();
		state=STOPPING;
		try{
			callOneWay("stop",args);
			state=STOPPED;
		}
		catch(PageException pe){
			state=FAILED;
			throw new PageGatewayException(pe);
		}
	}

	/**
	 * @see org.opencfml.eventgateway.Gateway#getHelper()
	 */
	public Object getHelper() {
		Struct args=new StructImpl(StructImpl.TYPE_LINKED);
		return callEL("getHelper",args,null);
	}

	/**
	 * @see org.opencfml.eventgateway.Gateway#getId()
	 */
	public String getId() {
		return id;
	}

	/**
	 * @see org.opencfml.eventgateway.Gateway#getState()
	 */
	public int getState() {
		Struct args=new StructImpl();
		Integer state=Constants.Integer(this.state);
		try {
			return GatewayEngineImpl.toIntState(Caster.toString(call("getState",args,state)),this.state);
		} 
		catch (PageException pe) {
			engine.log(this, GatewayEngine.LOGLEVEL_ERROR, pe.getMessage());
		}
		return this.state;
	}



	/**
	 * @see org.opencfml.eventgateway.Gateway#sendMessage(railo.runtime.type.Struct)
	 */
	public String sendMessage(Map data) throws GatewayException {
		Struct args=new StructImpl(StructImpl.TYPE_LINKED);
		args.setEL("data", Caster.toStruct(data, null, false));
		try {
			return Caster.toString(call("sendMessage",args,""));
		} catch (PageException pe) {
			throw new PageGatewayException(pe);
		}
	}
	
	private Object callEL(String methodName,Struct arguments, Object defaultValue)  {
		return engine.callEL(cfcPath,id, methodName, arguments, true, defaultValue);
	}
	
	private boolean callOneWay(String methodName,Struct arguments) throws PageException {
		return engine.callOneWay(cfcPath,id, methodName, arguments, true);
	}
	
	private Object call(String methodName,Struct arguments, Object defaultValue) throws PageException  {
		return engine.call(cfcPath,id, methodName, arguments, true, defaultValue);
	}

	/*private Object callEL(String methodName,Struct arguments, Object defaultValue)  {
		try {
			return call(methodName, arguments, defaultValue);
		} catch (PageException e) {
			return defaultValue;
		}
	}
	
	private boolean callOneWay(String methodName,Struct arguments) throws PageException {
		return call(methodName, arguments, OBJ)!=OBJ;
	}

	private Object call(String methodName,Struct arguments, Object defaultValue) throws PageException  {
		DevNullOutputStream os = DevNullOutputStream.DEV_NULL_OUTPUT_STREAM;

		PageContext oldPC = ThreadLocalPageContext.get();
		PageContextImpl pc = ThreadUtil.createPageContext(config, os, "localhost", requestURI, "", null, null, null, null);
		pc.setRequestTimeout(999999999999999999L);       
		try {
			ThreadLocalPageContext.register(pc);
			if(getCFC(pc).containsKey(methodName)){
				return getCFC(pc).callWithNamedValues(pc, methodName, arguments);
			}
		}
		finally{
			ThreadLocalPageContext.register(oldPC);
		}
		return defaultValue;
	}
	
	
	private Component getCFC(PageContextImpl pc) throws PageException{
		if(_cfc==null){
			Mapping m=new MappingImpl((ConfigImpl)config,"/",cfcDirectory.getAbsolutePath(),null,false,true,false,false,false);
			
			
			PageSource ps = m.getPageSource(requestURI);
			Page p = ps.loadPage((ConfigWeb)config);
			_cfc=ComponentLoader.loadComponentImpl(pc, p, ps, cfcPath, false);
			//pc.loadComponent(cfcPath);
		}
		return _cfc;
	}*/
}