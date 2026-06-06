package com.scripttool.config;

import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.model.entity.User;
import com.scripttool.repository.ProjectRepository;
import com.scripttool.repository.ScriptVersionRepository;
import com.scripttool.repository.UserRepository;
import com.scripttool.service.ChapterSplitService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ScriptVersionRepository scriptVersionRepository;
    private final ChapterSplitService chapterSplitService;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           ProjectRepository projectRepository, ScriptVersionRepository scriptVersionRepository,
                           ChapterSplitService chapterSplitService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.projectRepository = projectRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.chapterSplitService = chapterSplitService;
    }

    @Override
    public void run(String... args) {
        User admin;
        if (userRepository.count() == 0) {
            admin = new User(
                    "admin",
                    passwordEncoder.encode("123456"),
                    "管理员"
            );
            userRepository.save(admin);
            System.out.println("==============================================");
            System.out.println("  测试账号已创建");
            System.out.println("  用户名: admin");
            System.out.println("  密码:   123456");
            System.out.println("==============================================");
        } else {
            admin = userRepository.findByUsername("admin").orElse(null);
        }

        // 创建示例项目
        if (admin != null && projectRepository.count() == 0) {
            createDemoProject(admin.getId());
        }
    }

    private void createDemoProject(Long userId) {
        String demoText = "第一章 甄士隐梦幻识通灵\n\n"
            + "此开卷第一回也。作者自云：因曾历过一番梦幻之后，故将真事隐去，而借通灵之说，撰此《石头记》一书也。\n\n"
            + "列位看官：你道此书从何而来？说起根由虽近荒唐，细按则深有趣味。原来女娲氏炼石补天之时，于大荒山无稽崖炼成高经十二丈、方经二十四丈顽石三万六千五百零一块。娲皇氏只用了三万六千五百块，只单单的剩了一块未用，便弃在此山青埂峰下。\n\n"
            + "一日，正当嗟悼之际，俄见一僧一道远远而来，生得骨格不凡，丰神迥别，说说笑笑来至峰下，坐于石边高谈快论。先是说些云山雾海神仙玄幻之事，后便说到红尘中荣华富贵。此石听了，不觉打动凡心，也想要到人间去享一享这荣华富贵。\n\n"

            + "第二章 贾夫人仙逝扬州城\n\n"
            + "且说黛玉自那日弃舟登岸时，便有荣国府打发了轿子并拉行李的车辆久候了。这林黛玉常听得母亲说过，他外祖母家与别家不同。他近日所见的这几个三等仆妇，吃穿用度，已是不凡了，何况今至其家。\n\n"
            + "黛玉方进入房时，只见两个人搀着一位鬓发如银的老母迎上来，黛玉便知是他外祖母。方欲拜见时，早被他外祖母一把搂入怀中，心肝儿肉叫着大哭起来。当下地下侍立之人，无不掩面涕泣，黛玉也哭个不住。一时众人慢慢解劝住了，黛玉方拜见了外祖母。\n\n"
            + "一语未了，只听后院中有人笑声，说：\"我来迟了，不曾迎接远客！\"黛玉纳罕道：\"这些人个个皆敛声屏气，恭肃严整如此，这来者系谁，这样放诞无礼？\"心下想时，只见一群媳妇丫鬟围拥着一个人从后房门进来。这个人打扮与众姑娘不同，彩绣辉煌，恍若神妃仙子。\n\n"

            + "第三章 贾宝玉神游太虚境\n\n"
            + "彼时宝玉迷迷惑惑，若有所失。众人忙端上桂圆汤来，呷了两口，遂起身整衣。袭人伸手与他系裤带时，不觉伸手至大腿处，只觉冰凉一片沾湿，唬的忙退出手来，问是怎么了。宝玉红涨了脸，把他的手一捻。袭人本是个聪明女子，年纪本又比宝玉大两岁，近来也渐通人事，今见宝玉如此光景，心中便觉察一半了，不觉也羞的红涨了脸面，不敢再问。\n\n"
            + "自此宝玉视袭人更比别个不同，袭人待宝玉更为尽职。暂且别无话说。如今且说林黛玉自在荣府以来，贾母万般怜爱，寝食起居，一如宝玉，迎春、探春、惜春三个亲孙女倒且靠后。便是宝玉和黛玉二人之亲密友爱处，亦自较别个不同，日则同行同坐，夜则同息同止，真是言和意顺，略无参商。\n\n";

        Project demo = new Project(userId, "📖 红楼梦 改编示例 · 点击体验", demoText);
        demo.setChapterCount(3);
        demo.setStatus(Project.ProjectStatus.COMPLETED);
        projectRepository.save(demo);

        // 预生成示例 YAML 剧本
        String demoYaml = "script:\n"
            + "  title: \"红楼梦 改编剧本\"\n"
            + "  based_on: \"红楼梦\"\n"
            + "  author: \"AI 改编助手\"\n"
            + "  version: \"1.0\"\n"
            + "  language: zh-CN\n"
            + "  created_at: \"2026-06-06\"\n"
            + "\n"
            + "characters:\n"
            + "  - id: CHAR_001\n"
            + "    name: \"贾宝玉\"\n"
            + "    role: protagonist\n"
            + "    description: \"荣国府贾政之子，衔玉而生，性格叛逆多情\"\n"
            + "    traits: [\"多情\", \"叛逆\", \"聪慧\", \"怜香惜玉\"]\n"
            + "    first_appearance: SCENE_002\n"
            + "\n"
            + "  - id: CHAR_002\n"
            + "    name: \"林黛玉\"\n"
            + "    role: protagonist\n"
            + "    description: \"贾母外孙女，才华横溢，体弱多病，多愁善感\"\n"
            + "    traits: [\"才情\", \"敏感\", \"孤傲\", \"聪颖\"]\n"
            + "    first_appearance: SCENE_002\n"
            + "\n"
            + "  - id: CHAR_003\n"
            + "    name: \"王熙凤\"\n"
            + "    role: supporting\n"
            + "    description: \"荣国府管家，精明能干，泼辣狠毒\"\n"
            + "    traits: [\"精明\", \"泼辣\", \"能干\", \"圆滑\"]\n"
            + "    first_appearance: SCENE_002\n"
            + "\n"
            + "  - id: CHAR_004\n"
            + "    name: \"袭人\"\n"
            + "    role: supporting\n"
            + "    description: \"贾宝玉贴身丫鬟，温柔贤惠，忠心耿耿\"\n"
            + "    traits: [\"温柔\", \"贤惠\", \"忠心\", \"懂事\"]\n"
            + "    first_appearance: SCENE_003\n"
            + "\n"
            + "  - id: CHAR_005\n"
            + "    name: \"一僧一道\"\n"
            + "    role: minor\n"
            + "    description: \"茫茫大士、渺渺真人，引渡凡人的仙者\"\n"
            + "    traits: [\"超然\", \"智慧\", \"神秘\"]\n"
            + "    first_appearance: SCENE_001\n"
            + "\n"
            + "  - id: CHAR_006\n"
            + "    name: \"贾母\"\n"
            + "    role: supporting\n"
            + "    description: \"荣国府最高权威，慈祥和蔼的老祖宗\"\n"
            + "    traits: [\"慈祥\", \"威严\", \"溺爱\"]\n"
            + "    first_appearance: SCENE_002\n"
            + "\n"
            + "scenes:\n"
            + "  - id: SCENE_001\n"
            + "    chapter: 1\n"
            + "    scene_number: 1\n"
            + "    type: EXT\n"
            + "    location: \"大荒山青埂峰下\"\n"
            + "    time: \"远古时期\"\n"
            + "    description: \"云雾缭绕的仙山，女娲补天遗留的顽石静卧峰下，天地灵气氤氲\"\n"
            + "    characters: [CHAR_005]\n"
            + "    beats:\n"
            + "      - type: narration\n"
            + "        character: null\n"
            + "        line: \"女娲氏炼石补天之时，于大荒山无稽崖炼成顽石三万六千五百零一块，只单单剩了一块未用。\"\n"
            + "        direction: \"画面渐显仙山全景，镜头缓缓推近青埂峰\"\n"
            + "        emotion: null\n"
            + "\n"
            + "      - type: action\n"
            + "        character: null\n"
            + "        line: null\n"
            + "        direction: \"一僧一道从远处走来，步履从容，衣袂飘飘\"\n"
            + "        emotion: null\n"
            + "\n"
            + "      - type: dialogue\n"
            + "        character: CHAR_005\n"
            + "        line: \"此石倒也有些灵性，不如携它去那红尘中走一遭。\"\n"
            + "        direction: \"道者指着顽石，含笑而言\"\n"
            + "        emotion: \"淡然\"\n"
            + "\n"
            + "      - type: transition\n"
            + "        character: null\n"
            + "        line: null\n"
            + "        direction: \"淡出转场至扬州码头\"\n"
            + "        emotion: null\n"
            + "\n"
            + "  - id: SCENE_002\n"
            + "    chapter: 2\n"
            + "    scene_number: 1\n"
            + "    type: INT\n"
            + "    location: \"荣国府正房大院\"\n"
            + "    time: \"秋日午后\"\n"
            + "    description: \"雕梁画栋的富贵厅堂，众仆妇屏息以待，气氛庄重\"\n"
            + "    characters: [CHAR_002, CHAR_003, CHAR_006]\n"
            + "    beats:\n"
            + "      - type: action\n"
            + "        character: null\n"
            + "        line: null\n"
            + "        direction: \"林黛玉在丫鬟搀扶下步入正房，环视四周，眼中含怯\"\n"
            + "        emotion: null\n"
            + "\n"
            + "      - type: action\n"
            + "        character: null\n"
            + "        line: null\n"
            + "        direction: \"贾母一把将黛玉搂入怀中，放声大哭\"\n"
            + "        emotion: null\n"
            + "\n"
            + "      - type: dialogue\n"
            + "        character: CHAR_006\n"
            + "        line: \"心肝儿肉！你可来了！\"\n"
            + "        direction: \"老泪纵横，声音颤抖\"\n"
            + "        emotion: \"激动、悲伤\"\n"
            + "\n"
            + "      - type: action\n"
            + "        character: null\n"
            + "        line: null\n"
            + "        direction: \"王熙凤人未到笑先闻，一群丫鬟簇拥而入\"\n"
            + "        emotion: null\n"
            + "\n"
            + "      - type: dialogue\n"
            + "        character: CHAR_003\n"
            + "        line: \"我来迟了，不曾迎接远客！\"\n"
            + "        direction: \"笑声朗朗，目光扫过黛玉，上下打量\"\n"
            + "        emotion: \"爽朗、好奇\"\n"
            + "\n"
            + "      - type: dialogue\n"
            + "        character: CHAR_002\n"
            + "        line: \"（旁白）这些人个个皆敛声屏气，这来者系谁，这样放诞无礼？\"\n"
            + "        direction: \"黛玉暗自思忖，目光中带着惊讶\"\n"
            + "        emotion: \"惊讶、好奇\"\n"
            + "\n"
            + "  - id: SCENE_003\n"
            + "    chapter: 3\n"
            + "    scene_number: 1\n"
            + "    type: INT\n"
            + "    location: \"贾宝玉卧房\"\n"
            + "    time: \"深夜\"\n"
            + "    description: \"烛光摇曳的卧房，暖香弥漫，窗外月色朦胧\"\n"
            + "    characters: [CHAR_001, CHAR_004]\n"
            + "    beats:\n"
            + "      - type: action\n"
            + "        character: null\n"
            + "        line: null\n"
            + "        direction: \"宝玉从梦中惊醒，神情恍惚，若有所失\"\n"
            + "        emotion: null\n"
            + "\n"
            + "      - type: action\n"
            + "        character: null\n"
            + "        line: null\n"
            + "        direction: \"袭人捧桂圆汤至床前，细心服侍\"\n"
            + "        emotion: null\n"
            + "\n"
            + "      - type: dialogue\n"
            + "        character: CHAR_001\n"
            + "        line: \"我方才……做了一个奇梦。\"\n"
            + "        direction: \"低头喃喃自语，面色微红\"\n"
            + "        emotion: \"迷惘\"\n"
            + "\n"
            + "      - type: dialogue\n"
            + "        character: CHAR_004\n"
            + "        line: \"二爷快喝了这汤，定定神。\"\n"
            + "        direction: \"温柔地递上汤碗，目光关切\"\n"
            + "        emotion: \"温柔、关切\"\n"
            + "\n"
            + "      - type: monologue\n"
            + "        character: CHAR_001\n"
            + "        line: \"自黛玉来了之后，我竟觉得心里有什么东西不一样了……\"\n"
            + "        direction: \"面向窗外，月光洒在脸上\"\n"
            + "        emotion: \"懵懂、心动\"\n"
            + "\n"
            + "      - type: narration\n"
            + "        character: null\n"
            + "        line: \"自此宝玉视袭人更比别个不同，袭人待宝玉更为尽职。而黛玉与宝玉二人亲密友爱，日则同行同坐，夜则同息同止。\"\n"
            + "        direction: \"画面蒙太奇：宝玉黛玉园中嬉戏，袭人灯下缝补\"\n"
            + "        emotion: null\n";

        ScriptVersion demoVersion = new ScriptVersion(demo.getId(), 1, demoYaml);
        scriptVersionRepository.save(demoVersion);

        System.out.println("==============================================");
        System.out.println("  示例项目「红楼梦 改编示例」已创建");
        System.out.println("  包含 3 个章节 + 完整 YAML 剧本");
        System.out.println("  登录后即可体验完整流程");
        System.out.println("==============================================");
    }
}
