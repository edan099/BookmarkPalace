package com.longlong.bookmark.ui.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.io.IOException
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.*

/**
 * 打赏与联系对话框
 * 支持微信/支付宝不同金额打赏，以及联系方式
 */
class DonateDialog(project: Project?) : DialogWrapper(project) {

    init {
        title = "☕ 请作者喝杯咖啡 | Buy Me a Coffee"
        setOKButtonText("感谢支持 Thanks!")
        setCancelButtonText("关闭 Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(520, 580)
        mainPanel.border = JBUI.Borders.empty(10)

        // 顶部介绍
        val headerPanel = createHeaderPanel()
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // 中间Tab面板
        val tabbedPane = JBTabbedPane()
        
        // 微信打赏Tab
        tabbedPane.addTab("💚 微信 WeChat", createWeChatPanel())
        
        // 支付宝打赏Tab  
        tabbedPane.addTab("💙 支付宝 Alipay", createAlipayPanel())
        
        // 联系方式Tab
        tabbedPane.addTab("📧 联系 Contact", createContactPanel())

        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        // 底部信息
        val footerPanel = createFooterPanel()
        mainPanel.add(footerPanel, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun createHeaderPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(0, 0, 15, 0)

        val titleLabel = JBLabel("🏰 BookmarkPalace · 书签宫殿")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 18f)
        titleLabel.alignmentX = Component.CENTER_ALIGNMENT

        val descLabel = JBLabel("<html><center>如果这个插件对您有帮助，欢迎请作者喝杯咖啡 ☕<br>" +
                "If this plugin helps you, consider buying me a coffee!</center></html>")
        descLabel.alignmentX = Component.CENTER_ALIGNMENT
        descLabel.border = JBUI.Borders.empty(8, 0, 0, 0)

        panel.add(titleLabel)
        panel.add(descLabel)

        return panel
    }

    private fun createWeChatPanel(): JPanel {
        return createPaymentPanel(
            listOf(
                PaymentOption("¥1.88", "一根棒棒糖", "/donate/微信1块88.jpg"),
                PaymentOption("¥18.88", "一杯咖啡", "/donate/微信18块88.jpg"),
                PaymentOption("¥88.88", "请客吃饭", "/donate/微信88块88.jpg"),
                PaymentOption("自定义", "随心打赏", "/donate/微信自定义.jpg")
            ),
            JBColor(Color(7, 193, 96), Color(7, 193, 96))  // 微信绿
        )
    }

    private fun createAlipayPanel(): JPanel {
        return createPaymentPanel(
            listOf(
                PaymentOption("¥1.88", "一根棒棒糖", "/donate/支付宝1块88.jpg"),
                PaymentOption("¥18.88", "一杯咖啡", "/donate/支付宝18块88.jpg"),
                PaymentOption("¥88.88", "请客吃饭", "/donate/支付宝88块88.jpg"),
                PaymentOption("自定义", "随心打赏", "/donate/支付宝自定义.jpg")
            ),
            JBColor(Color(0, 166, 226), Color(0, 166, 226))  // 支付宝蓝
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun createPaymentPanel(options: List<PaymentOption>, accentColor: Color): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)

        // 金额选择按钮
        val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 5))
        val cardLayout = CardLayout()
        val qrPanel = JPanel(cardLayout)

        val buttonGroup = ButtonGroup()
        options.forEachIndexed { index, option ->
            val button = JToggleButton("<html><center><b>${option.amount}</b><br><font size='2'>${option.desc}</font></center></html>")
            button.preferredSize = Dimension(100, 50)
            button.isFocusPainted = false
            
            button.addActionListener {
                cardLayout.show(qrPanel, option.amount)
            }
            
            buttonGroup.add(button)
            buttonPanel.add(button)

            // 创建二维码面板
            val qrCard = createQRCodeCard(option.imagePath, option.amount)
            qrPanel.add(qrCard, option.amount)

            // 默认选中第一个
            if (index == 0) {
                button.isSelected = true
            }
        }

        panel.add(buttonPanel, BorderLayout.NORTH)
        panel.add(qrPanel, BorderLayout.CENTER)

        return panel
    }

    private fun createQRCodeCard(imagePath: String, amount: String): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)

        try {
            val imageStream = javaClass.getResourceAsStream(imagePath)
            if (imageStream != null) {
                val originalImage = ImageIO.read(imageStream)
                // 缩放图片到合适大小
                val scaledImage = originalImage.getScaledInstance(280, 280, Image.SCALE_SMOOTH)
                val imageLabel = JLabel(ImageIcon(scaledImage))
                imageLabel.horizontalAlignment = SwingConstants.CENTER
                panel.add(imageLabel, BorderLayout.CENTER)
            } else {
                val placeholder = JBLabel("<html><center>二维码加载失败<br>QR Code not found<br>$imagePath</center></html>")
                placeholder.horizontalAlignment = SwingConstants.CENTER
                panel.add(placeholder, BorderLayout.CENTER)
            }
        } catch (e: IOException) {
            val errorLabel = JBLabel("图片加载错误: ${e.message}")
            errorLabel.horizontalAlignment = SwingConstants.CENTER
            panel.add(errorLabel, BorderLayout.CENTER)
        }

        val tipLabel = JBLabel("<html><center>扫码支付 $amount<br>Scan to pay</center></html>")
        tipLabel.horizontalAlignment = SwingConstants.CENTER
        tipLabel.border = JBUI.Borders.empty(10, 0, 0, 0)
        panel.add(tipLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun createContactPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(20)

        // 左侧联系信息
        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.border = JBUI.Borders.empty(0, 0, 0, 20)

        val contactItems = listOf(
            ContactItem("📧 邮箱 Email", "edan_d@qq.com", null),
            ContactItem("📺 抖音 Douyin", "扫码关注 →", null)
        )

        contactItems.forEach { item ->
            val itemPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 5))
            
            val nameLabel = JBLabel("<html><b>${item.name}:</b></html>")
            nameLabel.preferredSize = Dimension(120, 25)
            itemPanel.add(nameLabel)

            if (item.link != null) {
                val linkButton = JButton("<html><u>${item.value}</u></html>")
                linkButton.isBorderPainted = false
                linkButton.isContentAreaFilled = false
                linkButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                linkButton.foreground = JBColor.BLUE
                linkButton.addActionListener {
                    try {
                        Desktop.getDesktop().browse(URI(item.link))
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                }
                itemPanel.add(linkButton)
            } else {
                val valueLabel = JBLabel(item.value)
                itemPanel.add(valueLabel)
            }

            itemPanel.alignmentX = Component.LEFT_ALIGNMENT
            infoPanel.add(itemPanel)
            infoPanel.add(Box.createVerticalStrut(10))
        }

        // 抖音二维码
        val douyinPanel = JPanel(BorderLayout())
        douyinPanel.border = JBUI.Borders.empty(10)
        
        try {
            val imageStream = javaClass.getResourceAsStream("/donate/抖音联系.jpg")
            if (imageStream != null) {
                val originalImage = ImageIO.read(imageStream)
                val scaledImage = originalImage.getScaledInstance(200, 200, Image.SCALE_SMOOTH)
                val imageLabel = JLabel(ImageIcon(scaledImage))
                imageLabel.horizontalAlignment = SwingConstants.CENTER
                douyinPanel.add(imageLabel, BorderLayout.CENTER)
                
                val tipLabel = JBLabel("<html><center>抖音扫码关注<br>Follow on Douyin</center></html>")
                tipLabel.horizontalAlignment = SwingConstants.CENTER
                tipLabel.border = JBUI.Borders.empty(10, 0, 0, 0)
                douyinPanel.add(tipLabel, BorderLayout.SOUTH)
            }
        } catch (e: IOException) {
            // 忽略错误
        }

        // 组合布局
        val contentPanel = JPanel(BorderLayout())
        contentPanel.add(infoPanel, BorderLayout.WEST)
        contentPanel.add(douyinPanel, BorderLayout.CENTER)

        panel.add(contentPanel, BorderLayout.CENTER)

        // 底部感谢语
        val thanksLabel = JBLabel("<html><center><br>感谢您的支持与反馈！<br>" +
                "Thanks for your support and feedback!</center></html>")
        thanksLabel.horizontalAlignment = SwingConstants.CENTER
        panel.add(thanksLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun createFooterPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.CENTER))
        panel.border = JBUI.Borders.empty(15, 0, 0, 0)

        val footerLabel = JBLabel("<html><center><font color='gray'>Made with ❤️ by Edan<br>" +
                "每一份支持都是我持续更新的动力！</font></center></html>")
        footerLabel.horizontalAlignment = SwingConstants.CENTER
        panel.add(footerLabel)

        return panel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction, cancelAction)
    }

    private data class PaymentOption(
        val amount: String,
        val desc: String,
        val imagePath: String
    )

    private data class ContactItem(
        val name: String,
        val value: String,
        val link: String?
    )
}
