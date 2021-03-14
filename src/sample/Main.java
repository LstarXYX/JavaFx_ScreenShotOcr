package sample;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.swing.clipboard.ClipboardUtil;
import com.baidu.aip.ocr.AipOcr;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 截图工具
 * 调用百度的OCR获取截图的文字
 */
public class Main extends Application {

    private final String CONFIG_NAME = "data.properties";   //配置文件名
    //baidu api
    private static String[] resourceKeys = new String[]{"appid","apiKey","secretKey"};
    private static final Map<String, String> resource = new HashMap<>();
    private String PATH = null;
    private AipOcr client = null;
    //线程池
    ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(2, 2, 3, TimeUnit.MINUTES, new LinkedBlockingQueue<>());

    private Stage primaryStage; //将窗口设置为全局变量便于使用
    private Button btn; //点击截屏按钮
    private ImageView imageView;    //显示截取后的图片
    private TextArea textArea;      //输出识别文字
    private Stage stage = new Stage(); //拖拽窗口
    private AnchorPane an = new AnchorPane(); //截屏背景
    private Scene scene = new Scene(an); //设置场景

    private Screen screen = Screen.getPrimary();    //获取屏幕信息
    private double screenWidth; //记录屏幕宽
    private double screeHeight; //记录屏幕高

    private double start_x; //记录截图开始x
    private double start_y; //记录截屏开始y

    private double end_x; //记录截图结束x
    private double end_y; //记录截屏结束y

    private HBox hBox = new HBox();  //用于拖拽框
    private Label label = new Label(); //拖拽大小提示框,用于显示截图大小
    private Button finishBtn = new Button("完成截图"); //完成截图按钮

    private double real_x; //拖拽实时坐标x
    private double real_y; //拖拽实时坐标y

    @Override
    public void start(Stage primaryStage) throws Exception {

        //将主窗口传出,作为全局变量方便使用
        this.primaryStage = primaryStage;

        //初始化界面
        initView();
        //初始化事件
        initEvent();
        //初始化配置
        getConfig();
    }


    /**
     * 方法名 MethodName initView
     * 参数 Params []
     * 返回值 Return void
     * 作者 Author 郑添翼 Taky.Zheng
     * 编写时间 Date 2019-05-29 19:01 ＞ω＜
     * 描述 Description TODO 初始化界面
     */
    private void initView(){

        //初始化主窗口
        imageView = new ImageView();
        btn = new Button("点击截屏");
        //初始化文本域
        textArea = new TextArea("识别内容");
        VBox root = new VBox(10,btn, imageView,textArea);
        root.setPadding(new Insets(10));


        //初始化拖拽窗口
        //stage.initOwner(primaryStage);
        an.setStyle("-fx-background-color: #ffffff11");
        an.setTranslateY(-23);
        stage.setScene(scene);
        screenWidth = screen.getBounds().getWidth();
        screeHeight = screen.getBounds().getHeight();
        stage.setWidth(screenWidth);
        stage.setHeight(screeHeight);
        scene.setFill(Color.valueOf("#00000030"));
        stage.initStyle(StageStyle.TRANSPARENT);


        //给他拖拽窗口设置样式
        hBox.setStyle("-fx-background-color: #ffffff00; -fx-border-width: 1; -fx-border-color: #ff0000");
        label.setStyle("-fx-background-color: #000000; -fx-text-fill: #ffffff");


        primaryStage.setScene(new Scene(root));
        primaryStage.setWidth(600);
        primaryStage.setHeight(600);
        primaryStage.setTitle("截屏工具1.0");
        primaryStage.show();
    }

    /**
     * 方法名 MethodName initEvent
     * 参数 Params []
     * 返回值 Return void
     * 作者 Author 郑添翼 Taky.Zheng
     * 编写时间 Date 2019-05-29 19:01 ＞ω＜
     * 描述 Description TODO 初始化事件
     */
    private void initEvent(){


        //给点击截图按钮设置事件
        btn.setOnAction(p ->{
            an.getChildren().clear();
            //隐藏主窗口
            primaryStage.hide();
            stage.show();
        });
        //设置快捷键 ALT+Z
        btn.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.Z && event.isAltDown()){
                an.getChildren().clear();
                //隐藏主窗口
                primaryStage.hide();
                stage.show();
            }
        });
        //设置end退出截屏窗口
        scene.setOnKeyPressed(p -> {
            if (p.getCode() == KeyCode.ESCAPE) {
                stage.close();
                primaryStage.show();
            }
        });

        //设置点击事件
        an.setOnMousePressed(p ->{

            //清空拖拽窗口控件的数据,以便重新设置
            an.getChildren().clear();
            hBox.setPrefWidth(0);
            hBox.setPrefHeight(0);
            label.setText("宽度: 0 高度: 0");

            //记录拖拽开始坐标
            start_x = p.getSceneX();
            start_y = p.getSceneY();

            //给拖拽框设置起始位置
            AnchorPane.setLeftAnchor(hBox, start_x);
            AnchorPane.setTopAnchor(hBox, start_y);

            //给信息提示框设置位置
            label.setLayoutX(start_x);
            label.setLayoutY(start_y - label.getHeight());

            //给拖拽窗口添加元素
            an.getChildren().add(hBox);
            an.getChildren().add(label);

            //添加完成截屏按钮,默认开始不显示
            finishBtn.setVisible(false);
            an.getChildren().add(finishBtn);

        });

        //设置拖拽检测
        an.setOnDragDetected(dragDetected -> an.startFullDrag());

        //设置拖拽事件
        an.setOnMouseDragOver(p -> {
            //获取实时拖拽的坐标
            end_x = p.getSceneX();
            end_y = p.getSceneY();

            //获取拖拽实时大小
            real_x = end_x - start_x;
            real_y = end_y - start_y;

            //给拖拽框设置大小
            hBox.setPrefWidth(real_x);
            hBox.setPrefHeight(real_y);

            //给提示框设置大小信息
            label.setText("宽度: " + real_x + " 高度: " + real_y);

        });

        //设置拖拽结束事件
        an.setOnMouseDragReleased(p ->{

            //设置完成截屏按钮位置
            AnchorPane.setLeftAnchor(finishBtn,end_x - finishBtn.getWidth());
            AnchorPane.setTopAnchor(finishBtn,end_y - finishBtn.getHeight());
            //结束是显示完成截图按钮
            finishBtn.setVisible(true);

        });

        //设置完成按钮事件
        finishBtn.setOnAction(p ->{
            try {
                getScreenImage();
            } catch (Exception e) {
                e.printStackTrace();
            }

        });


    }


    /**
     * 方法名 MethodName getScreenImage
     * 参数 Params []
     * 返回值 Return void
     * 作者 Author 郑添翼 Taky.Zheng
     * 编写时间 Date 2019-05-30 12:43 ＞ω＜
     * 描述 Description TODO 获取图片
     */
    private void getScreenImage() throws Exception {

        stage.close();

        Robot robot = new Robot();
        //创建一个矩形,使用截屏工具根据位置截取出矩形大小的图片
        Rectangle rectangle = new Rectangle((int)start_x,(int)start_y + 22,(int)real_x,(int)real_y);
        BufferedImage bufferedImage = robot.createScreenCapture(rectangle);

        //获取图片,并放置到ImageView中
        WritableImage writableImage = SwingFXUtils.toFXImage(bufferedImage, null);
        imageView.setImage(writableImage);

        //获取系统剪切板,存入截图
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putImage(writableImage);
        clipboard.setContent(content);
        //输出到桌面
        String name = UUID.fastUUID().toString();
        final String filePath = PATH+name+".png";
//        ImageIO.write(bufferedImage, "png", new File(PATH));
        //写入图片
        ImgUtil.write(bufferedImage,new File(filePath));
        JSONObject res = client.basicGeneral(filePath,new HashMap<String, String>());
        String text = getWordsFromJsonObj(res);
        //把文字放入系统剪切板中
        ClipboardUtil.setStr(text);
        //把文字放入内容中
        textArea.setText(text);
        //删除图片
        poolExecutor.execute(() -> FileUtil.del(filePath));
        primaryStage.show();
    }

    /**
     * 从jsonObject 提取 翻译后的文字
     * @param jsonObject
     * @return
     */
    private String getWordsFromJsonObj(JSONObject jsonObject){
        Object words_result = jsonObject.get("words_result");
        if (words_result == null){
            return "识别失败";
        }else {
            StringBuilder builder = new StringBuilder();
            JSONArray array = (JSONArray) words_result;
            for (int i = 0; i < array.length(); i++) {
                builder.append(array.getJSONObject(i).getString("words"));
                builder.append("\r\n");
            }
            return builder.toString();
        }
    }

    /**
     * 初始化配置 获取百度api的相关配置
     * @throws IOException
     */
    public void getConfig() throws IOException {
        //data.properties路径
        String path = this.getClass().getResource(CONFIG_NAME).getPath().trim();
        //图片保存路径
        PATH = path.substring(1,path.length()-CONFIG_NAME.length());

        Properties properties = new Properties();
        properties.load(this.getClass().getResourceAsStream(CONFIG_NAME));
        System.out.println(properties);
        for (String key : resourceKeys) {
            System.out.println(properties.getProperty(key));
            resource.put(key, properties.getProperty(key));
        }
        client = new AipOcr(resource.get(resourceKeys[0]),resource.get(resourceKeys[1]),resource.get(resourceKeys[2]));
    }




    /**
     * 方法名 MethodName main
     * 参数 Params [args]
     * 返回值 Return void
     * 作者 Author 郑添翼 Taky.Zheng
     * 编写时间 Date 2019-05-29 18:53 ＞ω＜
     * 描述 Description TODO 主函数
     */
    public static void main(String[] args) {
        launch(args);
    }
}
