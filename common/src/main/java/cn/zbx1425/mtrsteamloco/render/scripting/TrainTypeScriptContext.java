package cn.zbx1425.mtrsteamloco.render.scripting;

import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.MainClient;
import cn.zbx1425.mtrsteamloco.render.integration.TrainModelCapture;
import cn.zbx1425.sowcer.math.Matrices;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcerext.model.RawMesh;
import cn.zbx1425.sowcerext.model.RawModel;
import net.minecraft.resources.ResourceLocation;
import org.mozilla.javascript.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TrainTypeScriptContext {

    private final ExecutorService SCRIPT_THREAD = Executors.newSingleThreadExecutor();

    private Scriptable scope;

    public boolean isActive = false;

    public void load(Map<ResourceLocation, String> scripts) {
        Context rhinoCtx = Context.enter();
        try {
            scope = new ImporterTopLevel(rhinoCtx);

            scope.put("include", scope, new NativeJavaMethod(
                    ScriptResourceUtil.class.getMethod("includeScript", ResourceLocation.class), "includeScript"));

            scope.put("ModelManager", scope, Context.toObject(MainClient.modelManager, scope));
            scope.put("TrainModelCapture", scope, new NativeJavaClass(scope, TrainModelCapture.class));
            scope.put("Resources", scope, new NativeJavaClass(scope, ScriptResourceUtil.class));
            scope.put("Timing", scope, new NativeJavaClass(scope, ScriptTimingUtil.class));
            scope.put("StateTracker", scope, new NativeJavaClass(scope, StateTracker.class));

            scope.put("Matrices", scope, new NativeJavaClass(scope, Matrices.class));
            scope.put("Matrix4f", scope, new NativeJavaClass(scope, Matrix4f.class));
            scope.put("Vector3f", scope, new NativeJavaClass(scope, Vector3f.class));

            scope.put("GraphicsTexture", scope, new NativeJavaClass(scope, GraphicsTexture.class));
            scope.put("Color", scope, new NativeJavaClass(scope, Color.class));
            scope.put("RawModel", scope, new NativeJavaClass(scope, RawModel.class));
            scope.put("RawMesh", scope, new NativeJavaClass(scope, RawMesh.class));

            ScriptResourceUtil.scriptsToExecute = new ArrayList<>(scripts.entrySet());
            for (int i = 0; i < ScriptResourceUtil.scriptsToExecute.size(); i++) {
                Map.Entry<ResourceLocation, String> entry = ScriptResourceUtil.scriptsToExecute.get(i);
                ScriptResourceUtil.relativeBase = entry.getKey();
                rhinoCtx.evaluateString(scope, entry.getValue(), entry.getKey().toString(), 1, null);
            }
            isActive = true;
        } catch (Exception ex) {
            Main.LOGGER.error("Script", ex);
        } finally {
            Context.exit();
        }
    }

    public Future<?> callTrainFunction(String function, TrainScriptContext trainCtx) {
        if (!isActive) return null;
        return SCRIPT_THREAD.submit(() -> {
            if (Thread.currentThread().isInterrupted()) return;

            Context rhinoCtx = Context.enter();
            trainCtx.state = rhinoCtx.newObject(scope);
            try {
                Object createFunction = scope.get(function, scope);
                if (createFunction instanceof Function && createFunction != Scriptable.NOT_FOUND) {
                    Object[] functionParam = { trainCtx, trainCtx.state, trainCtx.train, trainCtx.trainExtra };
                    ((Function)createFunction).call(rhinoCtx, scope, scope, functionParam);
                    trainCtx.scriptFinished();
                }
            } catch (Exception ex) {
                Main.LOGGER.error("Script", ex);
                isActive = false;
            } finally {
                Context.exit();
            }
        });
    }
}
