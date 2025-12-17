package com.firefly.ragdemo.tool;

import com.firefly.ragdemo.vo.FileVO;
import com.firefly.ragdemo.RaGdemoApplication;
import com.firefly.ragdemo.entity.User;
import com.firefly.ragdemo.entity.UploadedFile;
import com.firefly.ragdemo.mapper.UploadedFileMapper;
import com.firefly.ragdemo.service.KnowledgeBaseBulkUploadService;
import com.firefly.ragdemo.service.RagIndexService;
import com.firefly.ragdemo.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 命令行工具：将目录下所有文件批量上传到公共知识库（默认 kb_shared_cpp_tutorial）。
 * 不启动Web服务，仅加载Spring上下文。
 *
 * 用法示例：
 * mvn -q -DskipTests -Dexec.mainClass=com.firefly.ragdemo.tool.BulkPublicKbUploader \
 *   exec:java -Dexec.args="dir=/abs/path/to/files user=admin kb=kb_shared_cpp_tutorial"
 */
@Slf4j
public class BulkPublicKbUploader {

    private static final String DEFAULT_KB = "kb_shared_cpp_tutorial";

    public static void main(String[] args) {
        Map<String, String> params = parseArgs(args);
        String dir = params.get("dir");
        String username = params.get("user");
        String kbId = params.getOrDefault("kb", DEFAULT_KB);
        long waitSeconds = parseLong(params.get("waitSeconds"), 180);
        boolean retryFailedOnce = !"false".equalsIgnoreCase(params.getOrDefault("retryFailedOnce", "true"));

        if (dir == null || username == null) {
            System.err.println("用法: dir=/abs/path user=<username> [kb=kb_shared_cpp_tutorial] [waitSeconds=180] [retryFailedOnce=true|false]");
            System.exit(1);
        }

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(RaGdemoApplication.class)
                .web(WebApplicationType.NONE)
                .run();
        try {
            UserService userService = ctx.getBean(UserService.class);
            KnowledgeBaseBulkUploadService bulkUploadService = ctx.getBean(KnowledgeBaseBulkUploadService.class);

            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isEmpty()) {
                System.err.println("用户不存在: " + username);
                System.exit(1);
            }
            User user = userOpt.get();

            System.out.printf("开始上传目录: %s -> 知识库: %s (用户: %s)%n", dir, kbId, username);
            List<FileVO> uploaded = bulkUploadService.uploadDirectory(Path.of(dir), user, kbId);
            System.out.printf("提交上传完成，总计: %d 个文件，开始轮询索引状态...%n", uploaded.size());

            if (!uploaded.isEmpty()) {
                pollUntilDone(ctx.getBean(UploadedFileMapper.class),
                        ctx.getBean(RagIndexService.class),
                        uploaded,
                        waitSeconds,
                        retryFailedOnce);
            }
            System.out.println("任务结束。");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            ctx.close();
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) {
            return map;
        }
        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;
            String cleaned = arg.startsWith("--") ? arg.substring(2) : arg;
            int idx = cleaned.indexOf('=');
            if (idx > 0 && idx < cleaned.length() - 1) {
                String k = cleaned.substring(0, idx);
                String v = cleaned.substring(idx + 1);
                map.put(k, v);
            }
        }
        return map;
    }

    private static long parseLong(String v, long def) {
        try {
            return v == null ? def : Long.parseLong(v);
        } catch (Exception e) {
            return def;
        }
    }

    private static void pollUntilDone(UploadedFileMapper uploadedFileMapper,
                                      RagIndexService ragIndexService,
                                      List<FileVO> uploaded,
                                      long waitSeconds,
                                      boolean retryFailedOnce) throws InterruptedException {
        Map<String, String> status = new HashMap<>();
        Set<String> retried = ConcurrentHashMap.newKeySet();
        for (FileVO vo : uploaded) {
            status.put(vo.getId(), "PROCESSING");
        }
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(waitSeconds);
        while (System.currentTimeMillis() < deadline && status.values().stream().anyMatch(s -> s.equals("PROCESSING") || s.equals("FAILED"))) {
            boolean allFinal = true;
            for (FileVO vo : uploaded) {
                Optional<UploadedFile> db = uploadedFileMapper.findById(vo.getId());
                if (db.isEmpty()) {
                    System.out.printf("文件[%s] 不存在数据库记录，跳过%n", vo.getId());
                    continue;
                }
                String st = db.get().getStatus().name();
                status.put(vo.getId(), st);
                if ("PROCESSING".equals(st)) {
                    allFinal = false;
                } else if ("FAILED".equals(st)) {
                    if (retryFailedOnce && retried.add(vo.getId())) {
                        System.out.printf("检测到 FAILED，重试索引: %s%n", vo.getFilename());
                        ragIndexService.indexFile(vo.getId());
                        allFinal = false; // 等待重试结果
                    } else {
                        System.out.printf("文件索引失败: %s (id=%s)%n", vo.getFilename(), vo.getId());
                    }
                }
            }
            if (allFinal) {
                break;
            }
            Thread.sleep(3000);
        }

        System.out.println("最终状态：");
        status.forEach((id, st) -> System.out.printf(" - %s : %s%n", id, st));
    }
}
