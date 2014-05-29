package batfish.logicblox;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jetty.client.HttpClient;

//TODO: uncomment after LB libs restored
/*
import com.logicblox.bloxweb.client.DelimImportOptions;
import com.logicblox.bloxweb.client.DelimServiceClient;
import com.logicblox.bloxweb.client.DelimTxn;
import com.logicblox.bloxweb.client.DelimTxnServiceClient;
import com.logicblox.bloxweb.client.ServiceClientException;
import com.logicblox.bloxweb.client.ServiceConnector;
import com.logicblox.bloxweb.client.TCPTransport;
import com.logicblox.bloxweb.client.Transports;
import com.logicblox.common.Option;
import com.logicblox.common.ProtoBufSession.Exception;
import com.logicblox.common.protocol.CommonProto;
import com.logicblox.connect.ConnectBlox.RevertDatabase;
import com.logicblox.connect.ConnectBloxSession;
import com.logicblox.connect.ConnectBloxWorkspace;
import com.logicblox.connect.ConnectBloxWorkspace.CreateBuilder;
import com.logicblox.connect.ProtoBufException.ExceptionContainer;
import com.logicblox.connect.Workspace;
import com.logicblox.connect.ConnectBlox.Request;
import com.logicblox.connect.ConnectBlox.Response;
import com.logicblox.connect.Workspace.Command.AddProject;
import com.logicblox.connect.Workspace.Relation;
import com.logicblox.connect.Workspace.Relation.Column;
import com.logicblox.connect.Workspace.Relation.DoubleColumn;
import com.logicblox.connect.Workspace.Relation.EntityColumn;
import com.logicblox.connect.Workspace.Relation.Int64Column;
import com.logicblox.connect.Workspace.Relation.StringColumn;
import com.logicblox.connect.Workspace.Relation.UInt64Column;
import com.logicblox.connect.Workspace.Result;
import com.logicblox.connect.Workspace.Result.Failure;
import com.logicblox.connect.Workspace.Result.QueryPredicate;
*/

import batfish.util.Util;

public class LogicBloxFrontend {
   private static final String BLOXWEB_HOSTNAME = "localhost";
   private static final int BLOXWEB_PORT = 8080;
   private static final String BLOXWEB_PROTOCOL = "http";
   public static final long BLOXWEB_TIMEOUT_MS = 31536000000l;
   private static final String SERVICE_DIR = "batfish";
//TODO: uncomment after LB libs restored
/*
   private static void closeSession(
         ConnectBloxSession<Request, Response> session) {
      try {
         // close down a regular session
         if (session != null)
            session.close();
      }
      catch (ConnectBloxSession.Exception e) {
         System.err
               .println("Encountered error while closing a ConnectBloxSession: "
                     + e.getMessage());
      }
   }
*/
   private boolean _assumedToExist;

//TODO: uncomment after LB libs restored
//   ConnectBloxSession<Request, Response> _cbSession;
   private EntityTable _entityTable;
   private String _regularHost;
   private int _regularPort;

//TODO: uncomment after LB libs restored
//   private ConnectBloxWorkspace _workspace;

   private String _workspaceName;

   public LogicBloxFrontend(String regularHost, int regularPort,
         String workspaceName, boolean assumedToExist) {
      _workspaceName = workspaceName;
      _regularHost = regularHost;
      _regularPort = regularPort;
      _assumedToExist = assumedToExist;
      _entityTable = null;
   }

   public String addProject(String projectPath, String additionalLibraryPath) {
      return null;
//TODO: uncomment after LB libs restored
/*
      AddProject ap = Workspace.Command.addProject(new File(projectPath), true,
            true, additionalLibraryPath);
      List<Workspace.Result> results = null;
      try {
         results = _workspace.transaction(Collections.singletonList(ap));
      }
      catch (com.logicblox.connect.WorkspaceReader.Exception e) {
         e.printStackTrace();
      }
      Result result = results.get(0);
      if (result instanceof Result.AddProject) {
         return null;
      }
      else {
         return results.toString();
      }
*/
   }

   public void close() {
//TODO: uncomment after LB libs restored
/*
      if (_workspace != null && _workspace.isOpen()) {
         try {
            _workspace.close();
         }
         catch (com.logicblox.connect.WorkspaceReader.Exception e) {
            e.printStackTrace();
         }
      }
      closeSession(_cbSession);
*/
   }

   public boolean connected() {
      return false;
//TODO: uncomment after LB libs restored
//      return _cbSession != null;
   }

//TODO: uncomment after LB libs restored
/*
   private ConnectBloxSession<Request, Response> createRegularSession()
         throws LBInitializationException {
      try {
         ConnectBloxSession.Builder b = ConnectBloxSession.Builder
               .newInstance();
         b.setHost(_regularHost);
         b.setPort(_regularPort);
         return b.build();
      }
      catch (ConnectBloxSession.Exception e) {
         throw new LBInitializationException(e);
      }
   }

   private void createWorkspace(boolean overwrite)
         throws LBInitializationException {

      CreateBuilder cb = CreateBuilder.newInstance(_workspaceName);
      cb.setOverwrite(true);
      cb.setSession(_cbSession);
      try {
         _workspace = cb.build();
      }
      catch (com.logicblox.connect.WorkspaceReader.Exception e) {
         throw new LBInitializationException(e);
      }
   }

   public String execNamedBlock(String blockName) {
      Workspace.Command.ExecuteNamedBlock command = Workspace.Command
            .executeNamedBlock(blockName);
      List<Result> results = null;
      try {
         results = _workspace.transaction(Collections.singletonList(command));
      }
      catch (com.logicblox.connect.WorkspaceReader.Exception e) {
         e.printStackTrace();
      }
      // Workspace.Result.ExecuteNamedBlock result =
      // (Workspace.Result.ExecuteNamedBlock) results
      // .get(0);
      if (results.get(0) instanceof Workspace.Result.ExecuteNamedBlock) {
         return null;
      }
      else {
         return results.toString();
      }
   }

   public void fillColumn(LBValueType valueType, List<String> textColumn,
         Column column) {
      EntityColumn ec;
      UInt64Column indexColumn;
      switch (valueType) {

      case ENTITY_INDEX_IP:
         ec = (EntityColumn) column;
         long[] ips = ((Int64Column) ec.getRefModeColumn().unwrap()).getRows();
         for (long ip : ips) {
            textColumn.add(Util.longToIp(ip));
         }
         break;

      case ENTITY_INDEX_FLOW:
         ec = (EntityColumn) column;
         BigInteger[] flowIndices = ((UInt64Column) ec.getIndexColumn()
               .unwrap()).getRows();
         for (BigInteger index : flowIndices) {
            textColumn.add(_entityTable.getFlow(index));
         }
         break;

      case ENTITY_INDEX_NETWORK:
         ec = (EntityColumn) column;
         BigInteger[] networkIndices = ((UInt64Column) ec.getIndexColumn()
               .unwrap()).getRows();
         for (BigInteger index : networkIndices) {
            textColumn.add(_entityTable.getNetwork(index));
         }
         break;

      case ENTITY_INDEX_INT:
         ec = (EntityColumn) column;
         indexColumn = (UInt64Column) ec.getIndexColumn().unwrap();
         for (BigInteger i : indexColumn.getRows()) {
            textColumn.add(i.toString());
         }
         break;

      case ENTITY_REF_INT:
         ec = (EntityColumn) column;
         long[] refIntLongs = ((Int64Column) ec.getRefModeColumn().unwrap())
               .getRows();
         for (Long l : refIntLongs) {
            textColumn.add(l.toString());
         }
         break;

      case ENTITY_REF_STRING:
         ec = (EntityColumn) column;
         String[] strings = ((StringColumn) ec.getRefModeColumn().unwrap())
               .getRows();
         for (String s : strings) {
            textColumn.add(s);
         }
         break;

      case STRING:
         StringColumn sColumn = (StringColumn) column;
         textColumn.addAll(Arrays.asList(sColumn.getRows()));
         break;

      case IP:
         long[] ipsAsLongs = ((Int64Column) column).getRows();
         for (Long ipAsLong : ipsAsLongs) {
            textColumn.add(Util.longToIp(ipAsLong));
         }
         break;

      case FLOAT:
         double[] doubles = ((DoubleColumn) column).getRows();
         for (Double d : doubles) {
            textColumn.add(d.toString());
         }
         break;

      case INT:
         long[] longs = ((Int64Column) column).getRows();
         for (Long l : longs) {
            textColumn.add(l.toString());
         }
         break;
      case ENTITY_INDEX_BGP_ADVERTISEMENT:
         ec = (EntityColumn) column;
         BigInteger[] advertIndices = ((UInt64Column) ec.getIndexColumn()
               .unwrap()).getRows();
         for (BigInteger index : advertIndices) {
            textColumn.add(_entityTable.getBgpAdvertisement(index));
         }
         break;

      default:
         throw new Error("Invalid LBValueType");
      }
   }

   public List<String> getPredicate(PredicateInfo predicateInfo,
         Relation relation, String relationName) throws QueryException {
      List<LBValueType> valueTypes = predicateInfo
            .getPredicateValueTypes(relationName);
      if (valueTypes == null) {
         throw new QueryException("Missing type information for relation: "
               + relationName);
      }
      boolean isFunction = predicateInfo.isFunction(relationName);
      String outputLine;
      List<String> output = new ArrayList<String>();

      List<Column> columns = relation.getColumns();
      ArrayList<ArrayList<String>> tableByColumns = new ArrayList<ArrayList<String>>();
      for (int i = 0; i < columns.size(); i++) {
         Column column = columns.get(i);
         ArrayList<String> textColumn = new ArrayList<String>();
         tableByColumns.add(textColumn);
         fillColumn(valueTypes.get(i), textColumn, column);
      }
      ArrayList<ArrayList<String>> tableByRows = new ArrayList<ArrayList<String>>();
      int numRows = tableByColumns.get(0).size();
      for (int i = 0; i < numRows; i++) {
         ArrayList<String> textRow = new ArrayList<String>();
         tableByRows.add(textRow);
         for (ArrayList<String> textColumn : tableByColumns) {
            textRow.add(textColumn.get(i));
         }
      }
      if (isFunction) {
         for (ArrayList<String> textRow : tableByRows) {
            String value;
            outputLine = relationName + "[";
            for (int i = 0; i < textRow.size() - 1; i++) {
               value = textRow.get(i);
               outputLine += value + ", ";
            }
            outputLine = outputLine.substring(0, outputLine.length() - 2);
            value = textRow.get(textRow.size() - 1);
            outputLine += "] = " + value + "\n";
            output.add(outputLine);
         }
      }
      else {
         for (ArrayList<String> textRow : tableByRows) {
            outputLine = relationName + "(";
            for (String value : textRow) {
               outputLine += value + ", ";
            }
            outputLine = outputLine.substring(0, outputLine.length() - 2);
            outputLine += ")\n";
            output.add(outputLine);
         }
      }
      return output;
   }

   public List<? extends List<String>> getPredicateRows(
         PredicateInfo predicates, Relation relation, String relationName) {
      List<LBValueType> valueTypes = predicates
            .getPredicateValueTypes(relationName);
      List<Column> columns = relation.getColumns();
      ArrayList<ArrayList<String>> tableByColumns = new ArrayList<ArrayList<String>>();
      for (int i = 0; i < columns.size(); i++) {
         Column column = columns.get(i);
         ArrayList<String> textColumn = new ArrayList<String>();
         tableByColumns.add(textColumn);
         fillColumn(valueTypes.get(i), textColumn, column);
      }
      ArrayList<ArrayList<String>> tableByRows = new ArrayList<ArrayList<String>>();
      int numRows = tableByColumns.get(0).size();
      for (int i = 0; i < numRows; i++) {
         ArrayList<String> textRow = new ArrayList<String>();
         tableByRows.add(textRow);
         for (ArrayList<String> textColumn : tableByColumns) {
            textRow.add(textColumn.get(i));
         }
      }
      return tableByRows;
   }
*/
   public void initEntityTable() {
      _entityTable = new EntityTable(this);
   }

//TODO: uncomment after LB libs restored
/*
   public void initialize() throws LBInitializationException {
      _cbSession = createRegularSession();
      if (!_assumedToExist) {
         createWorkspace(true);
      }
      else {
         openWorkspace();
      }
   }

   private void openWorkspace() throws LBInitializationException {
      ConnectBloxWorkspace.OpenBuilder ob = ConnectBloxWorkspace.OpenBuilder
            .newInstance(_workspaceName);
      ob.setSession(_cbSession);
      try {
         _workspace = ob.build();
      }
      catch (com.logicblox.connect.WorkspaceReader.Exception e) {
         throw new LBInitializationException(e);
      }
   }

   public void postFacts(Map<String, StringBuilder> factBins)
         throws ServiceClientException {
      String base = BLOXWEB_PROTOCOL + "://" + BLOXWEB_HOSTNAME + ":"
            + BLOXWEB_PORT + "/" + SERVICE_DIR + "/";
      TCPTransport transport = Transports.tcp(false);
      HttpClient client = transport.getHttpClient();
      client.setTimeout(BLOXWEB_TIMEOUT_MS);
      client.setIdleTimeout(BLOXWEB_TIMEOUT_MS);
      ServiceConnector connector = ServiceConnector.create()
            .setTransport(transport.start()).setGZIP(false);
      DelimTxnServiceClient txnClient = connector.setURI(base + "txn")
            .createDelimTxnClient();
      DelimTxn transaction = txnClient.start().result();
      for (String suffix : factBins.keySet()) {
         DelimServiceClient currentClient = connector.setURI(base + suffix)
               .createDelimClient();
         DelimImportOptions input = new DelimImportOptions(factBins.get(suffix)
               .toString().getBytes());
         currentClient.postDelimitedFile(input, transaction);
      }
      transaction.commit().result();
   }

   public Relation queryPredicate(String qualifiedPredicateName) {
      Workspace.Command.QueryPredicate qp = Workspace.Command
            .queryPredicate(qualifiedPredicateName);
      List<Result> results = null;
      try {
         results = _workspace.transaction(Collections.singletonList(qp));
      }
      catch (com.logicblox.connect.WorkspaceReader.Exception e) {
         e.printStackTrace();
      }
      try {
         QueryPredicate qpr = (QueryPredicate) results.get(0);
         return qpr.getRelation();
      }
      catch (ClassCastException e) {
         Failure failure = (Failure) results.get(0);
         try {
            _cbSession.close();
         }
         catch (Exception e1) {
            throw new Error(ExceptionUtils.getStackTrace(e1));
         }
         throw new Error(failure.getMessage());
      }
   }
*/
   public void removeBlock(String blockName) {
//TODO: uncomment after LB libs restored
/*
      try {
         Workspace.Command.RemoveBlock rem = Workspace.Command
               .removeBlock(blockName);

         // Execute the command as part of a self-contained transaction
         List<Workspace.Result> results = _workspace.transaction(Collections
               .singletonList(rem));
         // Now to check that our command succeeded
         if (results.size() == 1) {
            Workspace.Result result = results.get(0);
            if (result instanceof Workspace.Result.Failure) {
               Workspace.Result.Failure failResult = (Workspace.Result.Failure) result;
               System.err.println("RemoveBlock failed: "
                     + failResult.getMessage());
            }
            else if (result instanceof Workspace.Result.AddBlock) {
               Workspace.Result.AddBlock addResult = (Workspace.Result.AddBlock) result;
               Option<CommonProto.CompilationProblems> optProblems = addResult
                     .getProblems();
               if (optProblems.isSome()) {
                  System.err.println("There were problems removing the block: "
                        + optProblems.unwrap());
               }
               else {
                  System.out
                        .println("Block successfully removed from workspace!");
               }
            }
            else {
               System.err.println("Unexpected result "
                     + result.getClass().getName() + "!");
            }
         }
         else {
            System.err.println("Incorrect number of results!");
         }
      }
      catch (Workspace.Exception e) {
         System.err.println("Encountered error " + e.errorSort());
      }
*/
   }

   public String revertDatabase(String branchName) {
//TODO: uncomment after LB libs restored
/*
      RevertDatabase.Builder rb = RevertDatabase.newBuilder();
      rb.setWorkspace(_workspaceName);
      rb.setOlderBranch(branchName);
      RevertDatabase r = rb.build();
      Request.Builder reqBuild = Request.newBuilder();
      reqBuild.setRevertDatabase(r);
      Request revertRequest = reqBuild.build();
      try {
         Future<Response> futureResponse = _cbSession.call(revertRequest);
         Response response = futureResponse.get();
         if (response.hasException()) {
            ExceptionContainer exception = response.getException();
            if (exception.hasMessage()) {
               String message = exception.getMessage();
               return message;
            }
         }
      }
      catch (Exception | InterruptedException | ExecutionException e) {
         return ExceptionUtils.getStackTrace(e);
      }
*/
      return null;
   }

   public String startBloxWebServices() {
      String stdout = null;
      String stderr = null;
      Process proc;
      String[] execArray = {
            "bash",
            "-c",
            "lb web-server load-services -w " + _workspaceName };
      try {
         proc = Runtime.getRuntime().exec(execArray);
         stdout = IOUtils.toString(proc.getInputStream());
         stderr = IOUtils.toString(proc.getErrorStream());
      }
      catch (IOException e) {
         e.printStackTrace();
      }
      if (stderr.length() > 0) {
         return stderr;
      }
      else if (stdout.length() > 0) {
         return stdout;
      }
      else {
         return null;
      }

   }

   public String stopBloxWebServices() {
      String stdout = null;
      String stderr = null;
      Process proc;
      String[] execArray = {
            "bash",
            "-c",
            "lb web-server unload-services -w " + _workspaceName };
      try {
         proc = Runtime.getRuntime().exec(execArray);
         stdout = IOUtils.toString(proc.getInputStream());
         stderr = IOUtils.toString(proc.getErrorStream());
      }
      catch (IOException e) {
         e.printStackTrace();
      }
      if (stderr.length() > 0) {
         return stderr;
      }
      else if (stdout.length() > 0) {
         return stdout;
      }
      else {
         return null;
      }

   }

}
